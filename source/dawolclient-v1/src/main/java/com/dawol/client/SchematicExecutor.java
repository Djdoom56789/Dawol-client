package com.dawol.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Carries out a {@link HumanizedBuilder} plan.
 *
 * <p>The planner decides everything — order, pacing, mistakes. This class only acts, and it
 * acts the way a player does: walk into range, select the slot, face the point being clicked,
 * and place against a neighbouring surface.
 *
 * <p>One action runs as a small state machine, because a placement is not one decision:
 *
 * <ol>
 *   <li><b>Travel.</b> Rank the clicks that would produce the wanted block state
 *       ({@link SchematicPlacer}) and check the best one is visible and in reach. If not,
 *       walk toward it. Nothing is clicked until there is a click that will work.</li>
 *   <li><b>Aim.</b> Turn onto the exact point, holding still for a couple of ticks the way
 *       a person does, then confirm the line of sight survives the rotation.</li>
 *   <li><b>Click.</b> {@code MultiPlayerGameMode.useItemOn}, the same entry point the
 *       vanilla client uses. No packet is built by hand.</li>
 *   <li><b>Settle.</b> Judge the result against the world a few ticks later. A block that
 *       landed in the wrong state is broken and re-placed once; after that it is accepted
 *       and counted, because a fence that connects differently or a slab in water cannot be
 *       argued with.</li>
 *   <li><b>Clear.</b> A position holding the wrong block is broken first — that is what
 *       building over existing terrain means.</li>
 * </ol>
 *
 * <p>An action that cannot be carried out is <i>deferred</i>: the rest of the pass skips that
 * position and reports it, rather than retrying it forever. That is what keeps a build with a
 * handful of impossible blocks from appearing to work while doing nothing.
 */
public final class SchematicExecutor {

    /** Vanilla survival interaction range. Matching it exactly keeps this unremarkable. */
    private static final double REACH = 4.5D;

    /** Ticks to spend facing the target before placing. */
    private static final int AIM_TICKS = 2;

    /** Ticks between the click and judging what the world did with it. */
    private static final int SETTLE_TICKS = 3;

    /** Degrees the aim is pushed past the target on an overshoot slip. */
    private static final float OVERSHOOT_DEGREES = 35.0F;

    /**
     * How many verify rounds may pass without progress before the build is abandoned.
     * A position that can never be satisfied — already occupied by another block, or with
     * no reachable surface — would otherwise keep verification returning work forever.
     */
    private static final int MAX_VERIFY_ROUNDS = 4;

    /**
     * How many times one position may be walked to before the builder accepts it cannot get
     * there: one walk to come into reach, and one more to stand on the side the wanted
     * orientation needs. More than that is grinding.
     */
    private static final int MAX_WALKS = 2;

    /** How many blocks one position may have broken for it, including its own wrong state. */
    private static final int MAX_CLEARS = 2;

    /** Ticks a single block may take to break before it is treated as unbreakable. */
    private static final int BREAK_TICKS = 120;

    /** Ticks between destroy packets, so mining looks like mining rather than a burst. */
    private static final long BREAK_INTERVAL_MILLIS = 50L;

    /**
     * How far the player may move before the click candidates are ranked again.
     *
     * <p>Ranking predicts a state for every candidate click, which is the expensive part of
     * this loop. It is keyed to standing position rather than to the tick, because walking
     * changes nothing about which click is correct until the player has actually moved.
     */
    private static final double RANK_DISTANCE = 0.5D;

    private final HumanizedBuilder builder = new HumanizedBuilder();
    private final SchematicWalker walker = new SchematicWalker();

    private boolean running;

    private long waitUntilMillis;

    /** The action being carried out. Held across the whole of it, not just the click. */
    private HumanizedBuilder.PlannedAction current;

    /** Where the current action's state machine has got to. */
    private Phase phase = Phase.TRAVEL;

    /** Clicks that would work, best first, ranked from where the player stands. */
    private List<SchematicPlacer.Choice> choices = List.of();

    /** The click being used: the best one that is visible and in reach. */
    private SchematicPlacer.Choice chosen;

    /** Where the candidates were ranked from, so a walk does not re-rank on every tick. */
    private double rankedX = Double.NaN;
    private double rankedZ = Double.NaN;

    private int aimTicks;

    private int settleTicks;

    private int walks;

    private int clears;

    /** The block being broken, or null. */
    private BlockPos clearing;

    private int clearTicks;

    /**
     * Positions this pass has already given up on.
     *
     * <p>Cleared when a pass starts, so the next pass retries them: the world changes as the
     * build goes up, and a position with nowhere to place against usually gains one.
     */
    private final Set<BlockPos> deferred = new HashSet<>();

    /** Why the last action was given up on, for the report the caller makes. */
    private Blocked blocked = Blocked.NONE;

    /** Actions actually placed this pass, which is how the caller spots a pass going nowhere. */
    private int placed;

    /** Positions accepted with the wrong block state, after repair was tried and failed. */
    private int approximate;

    /** Verify rounds run without the outstanding count shrinking. */
    private int stalledVerifyRounds;

    /** Outstanding count from the previous verify round, to detect progress. */
    private int lastOutstanding = Integer.MAX_VALUE;

    /** The states of the action being carried out. */
    private enum Phase {
        TRAVEL,
        AIM,
        SETTLE,
        CLEAR
    }

    /** Why a position was given up on. Reported rather than swallowed. */
    public enum Blocked {
        NONE,
        /** The hotbar no longer holds what the action needs. */
        NO_MATERIAL,
        /** Nothing adjacent to click. */
        NO_SURFACE,
        /** It exists, but the builder cannot get to it. */
        UNREACHABLE,
        /** Something else is there and cannot be removed. */
        OCCUPIED
    }

    /** The outcome of one attempted action, so callers can report rather than guess. */
    public enum Outcome {
        IDLE,
        WAITING,
        WALKING,
        AIMING,
        PLACED,
        REMOVED,
        REFUSED_NO_SURFACE,
        REFUSED_OUT_OF_REACH,
        REFUSED_NOTHING_TO_PLACE,
        REFUSED_BLOCKED,
        REFUSED_OCCUPIED
    }

    public boolean isRunning() {
        return running;
    }

    public HumanizedBuilder builder() {
        return builder;
    }

    /** Why the build is not progressing, or {@link Blocked#NONE}. */
    public Blocked blocked() {
        return blocked;
    }

    /** Actions placed since the pass started. */
    public int placed() {
        return placed;
    }

    /** Positions left in the wrong state because the right state could not be produced. */
    public int approximate() {
        return approximate;
    }

    public void start() {
        running = true;
        waitUntilMillis = 0L;
        current = null;
        phase = Phase.TRAVEL;
        choices = List.of();
        chosen = null;
        deferred.clear();
        blocked = Blocked.NONE;
        placed = 0;
        // The wrong-state count is deliberately not reset here: it describes the build being
        // placed rather than one pass over it, and a per-pass number is not one anybody can act
        // on. It is cleared when the build itself is re-anchored or dropped.
        stalledVerifyRounds = 0;
        lastOutstanding = Integer.MAX_VALUE;
        beginCommon();
        builder.reset();
    }

    public void stop() {
        running = false;
        walker.release();
    }

    /**
     * Lets go of the player's keys without dropping the plan.
     *
     * <p>Used while a screen is open, where the walk has to sit still but the build should not
     * forget what it was doing.
     */
    public void release() {
        walker.release();
    }

    /**
     * Drops the plan and all pacing state. Wired to the client's level-change reset, so a
     * dimension change cannot replay a plan built for the previous world's absolute
     * coordinates or carry a pending wait across into the new world.
     */
    public void reset() {
        stop();
        waitUntilMillis = 0L;
        current = null;
        phase = Phase.TRAVEL;
        choices = List.of();
        chosen = null;
        deferred.clear();
        blocked = Blocked.NONE;
        placed = 0;
        approximate = 0;
        stalledVerifyRounds = 0;
        lastOutstanding = Integer.MAX_VALUE;
        beginCommon();
        // plan() with nothing to build clears the queue and rewinds the cursor, which is the
        // only way to drop the existing plan without a mutable handle on the builder's queue.
        builder.plan(List.of(), null, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
    }

    private void beginCommon() {
        aimTicks = AIM_TICKS;
        settleTicks = SETTLE_TICKS;
        walks = 0;
        clears = 0;
        clearing = null;
        clearTicks = 0;
    }

    // --- the loop ---

    /**
     * Advances the build by at most one action. Called once per client tick.
     *
     * <p>The two phases that outlive the action's own tick — settling on a result and
     * breaking a block — are handled before anything else, so they cannot be cut short by the
     * next action arriving.
     */
    public Outcome tick(Minecraft mc) {
        if (!running || mc.player == null || mc.level == null || mc.gameMode == null) {
            return Outcome.IDLE;
        }

        if (phase == Phase.SETTLE) {
            return settle(mc);
        }
        if (phase == Phase.CLEAR) {
            return clear(mc);
        }

        if (System.currentTimeMillis() < waitUntilMillis) {
            return Outcome.WAITING;
        }

        HumanizedBuilder.PlannedAction action = builder.peek();
        if (action == null) {
            return endPass(mc);
        }
        if (action != current) {
            beginAction(action);
        }

        return switch (phase) {
            case TRAVEL -> travel(mc, action);
            case AIM -> aim(mc, action);
            default -> Outcome.IDLE;
        };
    }

    private void beginAction(HumanizedBuilder.PlannedAction action) {
        current = action;
        phase = Phase.TRAVEL;
        choices = List.of();
        chosen = null;
        beginCommon();
    }

    /**
     * Every queued action is done, or the ones left cannot be done. Re-checks the world, so
     * deliberate slips get corrected and finished means finished.
     */
    private Outcome endPass(Minecraft mc) {
        walker.release(mc);
        ItemStack[] hotbar = hotbar(mc.player);
        int corrections = builder.verify(mc.level, HumanizedBuilder.ofHotbar(hotbar),
                mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                mc.player.getYRot(), mc.player.getXRot(), deferred::contains);
        if (corrections == 0) {
            running = false;
            stalledVerifyRounds = 0;
            lastOutstanding = Integer.MAX_VALUE;
            return Outcome.IDLE;
        }

        // A position that can never be satisfied keeps verification returning work, so the
        // executor would re-roll mistakes and pauses on every pass and never stop. Each round
        // runs one action per outstanding position, so the count can only shrink; once it
        // stops shrinking, the remainder is unsatisfiable.
        if (corrections >= lastOutstanding) {
            if (++stalledVerifyRounds > MAX_VERIFY_ROUNDS) {
                running = false;
                stalledVerifyRounds = 0;
                lastOutstanding = Integer.MAX_VALUE;
                builder.plan(List.of(), null, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
                return Outcome.IDLE;
            }
        } else {
            stalledVerifyRounds = 0;
        }
        lastOutstanding = corrections;
        current = null;
        phase = Phase.TRAVEL;
        return Outcome.IDLE;
    }

    // --- travel ---

    /**
     * Gets the player somewhere the wanted block can actually be placed from.
     *
     * <p>Two things make a click usable: the line of sight the server will re-check, and
     * reach. A click that cannot produce the block's wanted orientation is still usable — it
     * is a placement this build can live with — but the builder will walk to a better side
     * for it once, because orientation is what makes a build look like the schematic rather
     * than merely contain the right blocks.
     */
    private Outcome travel(Minecraft mc, HumanizedBuilder.PlannedAction action) {
        LocalPlayer player = mc.player;
        BlockPos target = action.pos();

        ItemStack stack = player.getInventory().getItem(action.slot());
        if (stack == null || stack.isEmpty()) {
            // The plan was built for a hotbar that has since changed. Nothing to place.
            return giveUp(mc, action, Blocked.NO_MATERIAL);
        }

        if (chosen == null) {
            if (needsRanking(player)) {
                choices = SchematicPlacer.rank(action.intended(), player, stack, target,
                        player.getEyePosition(), pos -> !mc.level.getBlockState(pos).isAir());
                rankedX = player.getX();
                rankedZ = player.getZ();
            }
            chosen = usable(mc, player, choices);
        }

        boolean settledFor = chosen != null
                && (chosen.perfect() || !Toggles.schematicWalk || walks >= MAX_WALKS);
        if (settledFor || (chosen == null && (!Toggles.schematicWalk || walks >= MAX_WALKS))) {
            if (chosen == null) {
                return giveUp(mc, action, Blocked.NO_SURFACE);
            }
            return occupied(mc, action) ? startClearing(mc, action) : aim(mc, action);
        }

        // Either nothing can be clicked from here, or the orientation needs a particular side.
        Vec3 goal = SchematicPlacer.standGoal(action.intended(), target, player.getEyePosition());
        SchematicWalker.Progress progress = walker.step(mc, goal, target.getY());
        if (progress == SchematicWalker.Progress.BLOCKED) {
            if (chosen != null) {
                return occupied(mc, action) ? startClearing(mc, action) : aim(mc, action);
            }
            return giveUp(mc, action, Blocked.UNREACHABLE);
        }
        if (progress == SchematicWalker.Progress.ARRIVED) {
            // Standing where the wanted orientation needs: rank again from here, and stop
            // relocating after this whether or not it helped.
            walks++;
            chosen = null;
            choices = List.of();
        }
        return Outcome.WALKING;
    }

    /** The best click that is in reach, faces the player, and survives a line-of-sight check. */
    private static SchematicPlacer.Choice usable(Minecraft mc, LocalPlayer player,
                                                List<SchematicPlacer.Choice> choices) {
        Vec3 eye = player.getEyePosition();
        for (SchematicPlacer.Choice choice : choices) {
            if (!withinReach(player, choice.click())) {
                continue;
            }
            if (!facesPlayer(eye, choice)) {
                continue;
            }
            if (!visible(mc.level, eye, choice)) {
                continue;
            }
            return choice;
        }
        return null;
    }

    /** True when the candidates were ranked from somewhere else, or have not been ranked yet. */
    private boolean needsRanking(LocalPlayer player) {
        if (choices.isEmpty()) {
            return true;
        }
        return Math.abs(player.getX() - rankedX) + Math.abs(player.getZ() - rankedZ) > RANK_DISTANCE;
    }

    /** True when the click point is on the side of the block the player is on. */
    public static boolean facesPlayer(Vec3 eye, SchematicPlacer.Choice choice) {
        Vec3 normal = new Vec3(choice.face().getStepX(), choice.face().getStepY(), choice.face().getStepZ());
        return eye.subtract(choice.click()).dot(normal) > 0.0D;
    }

    /**
     * Whether the click point is the first thing the player's eye line meets.
     *
     * <p>This is the check the server makes before it accepts an interaction. Doing it here
     * too means an occluded click is rejected locally, where it costs nothing, rather than
     * remotely, where it costs an interaction and a tick.
     */
    public static boolean visible(Level level, Vec3 eye, SchematicPlacer.Choice choice) {
        if (level == null) {
            return false;
        }
        Vec3 click = choice.click();
        Vec3 direction = click.subtract(eye);
        if (direction.lengthSqr() < 1.0E-6D) {
            return false;
        }
        // Reaching exactly to the surface can stop a hair short, so the ray is carried a
        // little past the point being clicked.
        Vec3 end = click.add(direction.normalize().scale(0.05D));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(choice.support());
    }

    /** True when something other than the wanted block, or the wanted block in another state, is there. */
    private static boolean needsClearing(Level level, HumanizedBuilder.PlannedAction action) {
        BlockState present = level.getBlockState(action.pos());
        return !present.isAir() && present != action.intended();
    }

    private boolean occupied(Minecraft mc, HumanizedBuilder.PlannedAction action) {
        return needsClearing(mc.level, action);
    }

    private Outcome startClearing(Minecraft mc, HumanizedBuilder.PlannedAction action) {
        if (!Toggles.schematicReplace || clears >= MAX_CLEARS) {
            return giveUp(mc, action, Blocked.OCCUPIED);
        }
        if (!withinReach(mc.player, centreOf(action.pos()))) {
            return giveUp(mc, action, Blocked.UNREACHABLE);
        }
        clears++;
        clearing = action.pos();
        clearTicks = 0;
        phase = Phase.CLEAR;
        walker.release(mc);
        return clear(mc);
    }

    // --- aim and click ---

    private Outcome aim(Minecraft mc, HumanizedBuilder.PlannedAction action) {
        LocalPlayer player = mc.player;
        if (chosen == null) {
            phase = Phase.TRAVEL;
            return Outcome.IDLE;
        }

        // Held across the turn and the click, so a walk cannot start between the two.
        phase = Phase.AIM;

        // A deliberate missed click consumes the action without placing anything. The
        // verification pass notices and re-queues it, which is the point.
        if (action.mistake() == Humanizer.Mistake.MISSED_CLICK) {
            waitUntilMillis = System.currentTimeMillis() + action.delayMillis();
            nextAction();
            return Outcome.PLACED;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 click = chosen.click();
        if (!withinReach(player, click) || !facesPlayer(eye, chosen) || !visible(mc.level, eye, chosen)) {
            // Walking broke the line of sight, or the click went out of reach: rank again.
            chosen = null;
            choices = List.of();
            phase = Phase.TRAVEL;
            return Outcome.REFUSED_OUT_OF_REACH;
        }

        // The plan aimed at the block; the click lands on a face of a neighbour. Turning onto
        // the click is a correction to that aim, so the plan's own error survives it.
        Vec3 centre = centreOf(action.pos());
        float yaw = action.yaw() + SchematicWalker.wrapDegrees(
                SchematicPlacer.yawTo(eye, click) - SchematicPlacer.yawTo(eye, centre));
        float pitch = action.pitch() + (SchematicPlacer.pitchTo(eye, click) - SchematicPlacer.pitchTo(eye, centre));

        walker.release(mc);
        player.setYRot(yaw);
        player.setXRot(pitch);
        if (aimTicks > 0) {
            aimTicks--;
            return Outcome.AIMING;
        }

        int slot = action.slot();
        if (!hasItemInSlot(player, slot)) {
            nextAction();
            return Outcome.REFUSED_NOTHING_TO_PLACE;
        }

        // WRONG_BLOCK places whatever else is in the hotbar, which leaves the position
        // wrong on purpose. The re-queue pass then corrects it, so the slip costs time.
        if (action.mistake() == Humanizer.Mistake.WRONG_BLOCK) {
            int wrong = firstOtherOccupiedSlot(player, slot);
            if (wrong >= 0) {
                slot = wrong;
            }
        }
        // Overshoot: rotate past the target before the interaction is sent, so the click
        // genuinely lands at the wrong angle and the position is left unplaced for
        // verification to re-queue. Applied after the click it would be a visual no-op,
        // because the interaction has already gone at the planned rotation.
        if (action.mistake() == Humanizer.Mistake.OVERSHOOT) {
            player.setYRot(yaw + OVERSHOOT_DEGREES);
        }

        player.getInventory().setSelectedSlot(slot);

        BlockHitResult hit = new BlockHitResult(click, chosen.face(), chosen.support(), false);
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);

        waitUntilMillis = System.currentTimeMillis() + action.delayMillis();
        settleTicks = SETTLE_TICKS;
        phase = Phase.SETTLE;

        if (!result.consumesAction()) {
            return Outcome.REFUSED_NO_SURFACE;
        }
        placed++;
        return Outcome.PLACED;
    }

    // --- settle ---

    /**
     * Judges what the world did with the last click.
     *
     * <p>A block that landed in the right state is done. One that landed in the wrong state is
     * broken and placed again once — that is a stair facing the wrong way being fixed rather
     * than accepted. Anything else is left to the verification pass, which re-queues positions
     * whose block is simply absent.
     */
    private Outcome settle(Minecraft mc) {
        if (System.currentTimeMillis() < waitUntilMillis) {
            return Outcome.WAITING;
        }
        if (settleTicks > 0) {
            settleTicks--;
            return Outcome.WAITING;
        }

        HumanizedBuilder.PlannedAction action = current;
        if (action == null) {
            phase = Phase.TRAVEL;
            return Outcome.IDLE;
        }

        BlockState present = mc.level.getBlockState(action.pos());
        boolean wrongState = present != action.intended() && present.getBlock() == action.intended().getBlock();
        if (wrongState && Toggles.schematicReplace && clears < MAX_CLEARS) {
            clears++;
            clearing = action.pos();
            clearTicks = 0;
            phase = Phase.CLEAR;
            return clear(mc);
        }
        if (wrongState) {
            approximate++;
        }

        nextAction();
        return Outcome.PLACED;
    }

    // --- clearing ---

    /**
     * Breaks whatever is standing where the schematic wants its block.
     *
     * <p>This goes through the same calls the vanilla client makes while the attack key is
     * held: start, then continue each tick until the block gives. Nothing is removed
     * directly, so the server sees ordinary mining.
     */
    private Outcome clear(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (clearing == null) {
            phase = Phase.TRAVEL;
            return Outcome.IDLE;
        }

        if (mc.level.getBlockState(clearing).isAir()) {
            return finishClear(mc);
        }
        if (++clearTicks > BREAK_TICKS) {
            // Bedrock, a protected area, or a block this tool cannot touch. Stop rather than
            // stand there swinging.
            clearing = null;
            phase = Phase.TRAVEL;
            return giveUp(mc, current, Blocked.OCCUPIED);
        }
        if (System.currentTimeMillis() < waitUntilMillis) {
            return Outcome.WAITING;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 centre = centreOf(clearing);
        player.setYRot(SchematicPlacer.yawTo(eye, centre));
        player.setXRot(SchematicPlacer.pitchTo(eye, centre));
        Direction face = nearestFace(eye, centre);

        if (mc.gameMode.isDestroying()) {
            mc.gameMode.continueDestroyBlock(clearing, face);
        } else {
            mc.gameMode.startDestroyBlock(clearing, face);
        }
        waitUntilMillis = System.currentTimeMillis() + BREAK_INTERVAL_MILLIS;

        // Breaking over several ticks is not a placement; the caller only needs to know the
        // action is still in progress.
        return Outcome.REMOVED;
    }

    private Outcome finishClear(Minecraft mc) {
        clearing = null;
        phase = Phase.TRAVEL;
        // The world changed underneath the choice that was ranked against it.
        chosen = null;
        choices = List.of();
        walker.release(mc);
        return Outcome.REMOVED;
    }

    /** The face of a block the player is nearest to, for the destroy packet. */
    public static Direction nearestFace(Vec3 eye, Vec3 centre) {
        Vec3 delta = eye.subtract(centre);
        double ax = Math.abs(delta.x);
        double ay = Math.abs(delta.y);
        double az = Math.abs(delta.z);
        if (ax >= ay && ax >= az) {
            return delta.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (ay >= az) {
            return delta.y >= 0.0D ? Direction.UP : Direction.DOWN;
        }
        return delta.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    // --- bookkeeping ---

    /** Retires the current action as done and moves the cursor on. */
    private void nextAction() {
        builder.next();
        current = null;
        phase = Phase.TRAVEL;
        chosen = null;
        choices = List.of();
    }

    /**
     * Gives up on one position for the rest of the pass.
     *
     * <p>The rest of the build is not held up by it. The position is retried on the next pass,
     * which is what lets a block that needs a neighbour gets its chance once that neighbour
     * exists — and the caller's progress check is what stops that from looping forever.
     */
    private Outcome giveUp(Minecraft mc, HumanizedBuilder.PlannedAction action, Blocked reason) {
        blocked = reason;
        if (action != null) {
            deferred.add(action.pos());
        }
        walker.release(mc);
        nextAction();
        skipDeferred();
        return switch (reason) {
            case NO_MATERIAL -> Outcome.REFUSED_NOTHING_TO_PLACE;
            case UNREACHABLE -> Outcome.REFUSED_BLOCKED;
            case OCCUPIED -> Outcome.REFUSED_OCCUPIED;
            default -> Outcome.REFUSED_NO_SURFACE;
        };
    }

    /** Steps over the positions this pass has already given up on. */
    private void skipDeferred() {
        int guard = builder.remaining() + 1;
        while (guard-- > 0) {
            HumanizedBuilder.PlannedAction next = builder.peek();
            if (next == null || !deferred.contains(next.pos())) {
                return;
            }
            builder.next();
        }
    }

    /** An occupied hotbar slot other than {@code avoid}, for the wrong-block slip. */
    private static int firstOtherOccupiedSlot(LocalPlayer player, int avoid) {
        for (int i = 0; i < 9; i++) {
            if (i == avoid) {
                continue;
            }
            if (hasItemInSlot(player, i)) {
                return i;
            }
        }
        return -1;
    }

    private static Vec3 centreOf(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    // --- geometry, kept for the callers that only need the shape of a placement ---

    /** Where a placement will be anchored: the support block, its face, and the point clicked. */
    public record Surface(BlockPos support, Direction face, Vec3 clickVec, BlockPos clickPos) {
    }

    /**
     * Picks the neighbour to click when placing at {@code target}.
     *
     * <p>Tries floor, then walls, then ceiling, and returns the first whose support block is
     * solid. The clicked position is the support block itself, which is what the server
     * validates the interaction against.
     */
    public static Surface chooseSurface(Level level, LocalPlayer player, BlockPos target) {
        return chooseSurface(target, pos -> !level.getBlockState(pos).isAir());
    }

    /**
     * Pure form of {@link #chooseSurface}: given a solidity test, pick the attachment face.
     *
     * <p>Separated from the world so the geometry can be verified directly. Floor is
     * preferred, then walls, then ceiling, because that matches how a person builds.
     */
    public static Surface chooseSurface(BlockPos target, Predicate<BlockPos> isSolid) {
        for (Direction face : SchematicPlacer.FACE_ORDER) {
            BlockPos support = target.relative(face);
            if (!isSolid.test(support)) {
                continue;
            }
            Direction clickedFace = face.getOpposite();
            Vec3 click = SchematicPlacer.clickPoint(support, clickedFace, 0.5D, 0.5D);
            return new Surface(support, clickedFace, click, support);
        }
        return null;
    }

    /** Eye-to-click-point distance against vanilla survival reach. */
    public static boolean withinReach(LocalPlayer player, Vec3 clickPoint) {
        return player.getEyePosition().distanceTo(clickPoint) <= REACH;
    }

    /** True when the given hotbar slot holds anything at all. */
    public static boolean hasItemInSlot(LocalPlayer player, int slot) {
        if (slot < 0 || slot > 8) {
            return false;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        return stack != null && !stack.isEmpty();
    }

    /** Snapshot of the hotbar in slots 0-8. */
    public static ItemStack[] hotbar(LocalPlayer player) {
        ItemStack[] out = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            out[i] = player.getInventory().getItem(i);
        }
        return out;
    }
}
