package com.dawol.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a schematic's block list into a queue of human-looking actions.
 *
 * <p>The planner is deliberately separated from execution. Everything that decides
 * <i>what</i> the builder will do — order, pause length, aim error, whether to slip — is
 * computed here and is testable without a world or a GL context. Execution is the thin
 * part that carries the plan out.
 *
 * <p>Humanisation comes from three sources, all configurable:
 * <ul>
 *   <li><b>Variable pacing.</b> Every delay is drawn fresh, with occasional longer pauses,
 *       so the build never settles into a fixed interval.</li>
 *   <li><b>Imperfect aim.</b> Rotation carries an error that grows with distance, the way
 *       real aim degrades at range.</li>
 *   <li><b>Recoverable mistakes.</b> Some actions deliberately fail — the wrong block is
 *       placed, a click is missed, or the aim overshoots. Nothing stays broken: the plan is
 *       verified against the world afterwards, so a botched placement is re-queued and
 *       corrected. That verify-and-retry loop is what a person does.</li>
 * </ul>
 */
public final class HumanizedBuilder {

    /**
     * One planned action.
     *
     * @param slot        hotbar slot 0-8 holding the block to place
     * @param pos         target position
     * @param intended    the state the schematic wants
     * @param yaw         planned yaw, already carrying aim error
     * @param pitch       planned pitch, already carrying aim error
     * @param delayMillis pause before acting
     * @param mistake     deliberate error to make, or null
     */
    public record PlannedAction(int slot, BlockPos pos, BlockState intended,
                                float yaw, float pitch, long delayMillis,
                                Humanizer.Mistake mistake) {

        public boolean isMistake() {
            return mistake != null;
        }
    }

    /**
     * Resolves the hotbar slot holding a given item, or -1.
     *
     * <p>Indirection exists so planning never builds an {@code ItemStack}: outside a loaded
     * world its data components are unbound and constructing one throws. That keeps this
     * class pure logic, and testable without a game.
     */
    @FunctionalInterface
    public interface HotbarLookup {
        int slotFor(Item item);
    }

    private final List<PlannedAction> queue = new ArrayList<>();
    private int index = 0;
    private int plannedMistakes = 0;

    public HumanizedBuilder() {
    }

    /**
     * Builds a plan for {@code placements}.
     *
     * <p>Ordered bottom-up by layer, which is the only order that does not leave blocks
     * floating, with a distance-based secondary sort for determinism. Positions whose block
     * is not in the hotbar are skipped, since planning around a block the player cannot
     * place would fail every time.
     */
    public int plan(List<SchematicBuilder.Placement> placements, HotbarLookup hotbar,
                    double eyeX, double eyeY, double eyeZ,
                    float baseYaw, float basePitch) {
        queue.clear();
        index = 0;
        plannedMistakes = 0;

        if (placements == null) {
            return 0;
        }

        // Bottom-up by layer, then nearest first inside each layer. Inheriting the file's own
        // order instead would make the build order a property of the exporter rather than of
        // the build.
        for (SchematicBuilder.Placement placement : SchematicBuilder.order(placements, eyeX, eyeY, eyeZ)) {
            if (placement == null) {
                continue;
            }

            int slot = hotbar == null ? -1 : hotbar.slotFor(SchematicPlacer.itemFor(placement.state()));
            if (slot < 0) {
                continue;
            }

            double dx = placement.pos().getX() + 0.5D - eyeX;
            double dy = placement.pos().getY() + 0.5D - eyeY;
            double dz = placement.pos().getZ() + 0.5D - eyeZ;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float wantPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

            // Aim error scaled by distance, with an independent random sign per axis so the
            // error does not consistently lean one way.
            float error = Humanizer.aimErrorDegrees(distance);
            float yaw = wantYaw + error * Humanizer.randomSign() * 0.5F;
            float pitch = wantPitch + error * Humanizer.randomSign() * 0.3F;

            long delay = Humanizer.placementDelay() + Humanizer.occasionalPause();

            Humanizer.Mistake mistake = Humanizer.rollsMistake() ? Humanizer.rollMistakeType() : null;
            if (mistake != null) {
                plannedMistakes++;
            }

            queue.add(new PlannedAction(slot, placement.pos(), placement.state(),
                    yaw, pitch, delay, mistake));
        }

        // The count, not a boolean: a caller has to be able to tell "planned nothing because
        // the hotbar cannot build any of it" from "planned nothing because there is nothing
        // left to build".
        return queue.size();
    }

    /** The next action, or null when the plan is exhausted. Does not consume it. */
    public PlannedAction peek() {
        return index < queue.size() ? queue.get(index) : null;
    }

    /** Consumes and returns the next action. */
    public PlannedAction next() {
        PlannedAction action = peek();
        if (action != null) {
            index++;
        }
        return action;
    }

    public int remaining() {
        return Math.max(0, queue.size() - index);
    }

    public int planned() {
        return queue.size();
    }

    public int plannedMistakes() {
        return plannedMistakes;
    }

    public boolean isFinished() {
        return index >= queue.size();
    }

    public void reset() {
        index = 0;
    }

    public List<PlannedAction> actions() {
        return List.copyOf(queue);
    }

    /**
     * Re-queues every placement the world does not already satisfy.
     *
     * <p>This is the recovery half of the mistake system: a deliberate error leaves a
     * position wrong, and running verification against the real world turns those into
     * fresh actions. Mistakes therefore cost time rather than correctness.
     *
     * @return number of corrections queued
     */
    public int verify(Level level, HotbarLookup hotbar,
                      double eyeX, double eyeY, double eyeZ,
                      float baseYaw, float basePitch) {
        return verify(level, hotbar, eyeX, eyeY, eyeZ, baseYaw, basePitch, pos -> false);
    }

    /**
     * As {@link #verify(Level, HotbarLookup, double, double, double, float, float)}, but
     * leaving out positions the caller has already decided it cannot reach.
     *
     * <p>Without that filter a position the builder gave up on would come straight back as a
     * correction, and the pass would run forever producing actions it has already refused.
     *
     * @param skip positions to leave alone this pass
     */
    public int verify(Level level, HotbarLookup hotbar,
                      double eyeX, double eyeY, double eyeZ,
                      float baseYaw, float basePitch,
                      java.util.function.Predicate<BlockPos> skip) {
        if (level == null) {
            return 0;
        }

        List<SchematicBuilder.Placement> outstanding = new ArrayList<>();
        for (PlannedAction action : queue) {
            if (skip != null && skip.test(action.pos())) {
                continue;
            }
            if (!placed(level, action.pos(), action.intended())) {
                outstanding.add(new SchematicBuilder.Placement(action.pos(), action.intended()));
            }
        }

        if (outstanding.isEmpty()) {
            return 0;
        }

        plan(outstanding, hotbar, eyeX, eyeY, eyeZ, baseYaw, basePitch);
        return outstanding.size();
    }

    /**
     * Whether the world already holds what the schematic wants at {@code pos}.
     *
     * <p>Compares the block rather than the full state: chasing exact states would make
     * recovery loop forever on a stair that is merely rotated.
     */
    public static boolean placed(Level level, BlockPos pos, BlockState intended) {
        Block actual = level.getBlockState(pos).getBlock();
        return actual == intended.getBlock();
    }

    /**
     * Adapter for a real hotbar. Only safe with a loaded world.
     *
     * <p>Skips empty stacks, which are safe to test because {@code isEmpty()} reads no
     * components.
     */
    public static HotbarLookup ofHotbar(ItemStack[] hotbar) {
        return item -> {
            if (hotbar == null || item == null) {
                return -1;
            }
            for (int i = 0; i < hotbar.length && i < 9; i++) {
                ItemStack stack = hotbar[i];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (stack.getItem() == item) {
                    return i;
                }
            }
            return -1;
        };
    }
}