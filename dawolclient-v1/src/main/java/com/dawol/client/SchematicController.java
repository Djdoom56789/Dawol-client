package com.dawol.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Owns the loaded schematic: which file, where it is anchored, and what the builder is
 * currently doing with it.
 *
 * <p>This is the layer that was missing. The reader, the planner and the executor all
 * existed and were tested, but nothing joined them up, so switching the module on started an
 * executor with an empty plan and nothing happened. The join is deliberately more than a
 * one-off call:
 *
 * <ul>
 *   <li><b>Anchoring.</b> Reader positions are relative to the schematic's own origin, which
 *       is routinely negative. They only become world coordinates once an anchor is chosen,
 *       and re-anchoring must not require re-reading the file.</li>
 *   <li><b>Re-planning.</b> The planner can only queue blocks whose item is in the hotbar, so
 *       a plan is only ever valid for the hotbar it was built from. Picking up a shulker of
 *       stone has to bring the rest of the build back into the plan, and emptying a slot has
 *       to take it back out — otherwise the builder either starves or quietly declares a
 *       half-finished build complete.</li>
 *   <li><b>Getting there.</b> A build is bigger than an arm. The executor walks to the
 *       placements it cannot reach, and reports the ones it cannot walk to, because everything
 *       past the first few blocks would otherwise be skipped and the build would call itself
 *       finished.</li>
 *   <li><b>Saying why it stopped.</b> The two ways a build stops are "finished" and "cannot
 *       proceed", and they look identical from outside. Distinguishing them is the difference
 *       between a feature and a bug report.</li>
 * </ul>
 *
 * <p>All state lives on the client thread, mutated only from {@link #tick}.
 */
public final class SchematicController {

    private static final SchematicExecutor EXECUTOR = new SchematicExecutor();

    /** Slots in the hotbar; the planner can only ever use these. */
    private static final int HOTBAR_SLOTS = 9;

    /**
     * Passes in a row that may place nothing before the build is called stalled.
     *
     * <p>One is a hiccup: a re-plan against a hotbar that just changed, or a walk starting
     * over. Two in a row means the build is not going anywhere, and saying so is the whole
     * point — the alternative is a builder that looks busy and never finishes.
     */
    private static final int MAX_NO_PROGRESS_ROUNDS = 2;

    /** What the controller believes the build is doing, for the HUD and the screen. */
    public enum State {
        /** No file loaded. */
        NONE,
        /** Loaded, but nothing is being placed this tick. */
        IDLE,
        /** The executor is placing. */
        RUNNING,
        /** The executor is walking to the next placement. */
        WALKING,
        /** Nothing outstanding: the world already matches the schematic. */
        COMPLETE,
        /** Work outstanding that the planner cannot queue or the executor cannot reach. */
        STALLED
    }

    /**
     * One block type the schematic needs.
     *
     * @param needed   how many placements want this item
     * @param inHotbar 1 when the hotbar holds it, 0 when it does not; the latter is what
     *                 stalls a build, and nothing else about the stack matters to the planner
     */
    public record Material(Item item, int needed, int inHotbar) {
    }

    private static boolean loaded;
    private static Path file;
    private static String name = "";
    private static String format = "";
    private static Vec3i size = Vec3i.ZERO;
    private static String error;
    private static int total;
    private static int skipped;
    private static int regions;
    private static int dropped;

    /** Placements exactly as the file describes them: relative to the schematic's origin. */
    private static List<SchematicBuilder.Placement> relative = List.of();

    /** The same placements shifted onto {@link #anchor}, which is what the executor acts on. */
    private static List<SchematicBuilder.Placement> absolute = List.of();

    private static BlockPos anchor;

    private static State state = State.NONE;

    /** Set when the plan must be rebuilt before the next executor tick. */
    private static boolean needsReplan = true;

    /** Hotbar contents the current plan was built from, compared item-by-item each tick. */
    private static final Item[] hotbarSignature = new Item[HOTBAR_SLOTS];
    private static boolean signatureValid;

    /** Built on demand and dropped whenever the plan or the hotbar changes. */
    private static List<Material> materialCache;

    /**
     * Why the build is not moving, for the HUD and the screen. Empty whenever it is moving.
     *
     * <p>A stopped build and a finished one look identical from outside, so the reason is
     * kept rather than logged and forgotten.
     */
    private static String stallReason = "";

    /** Consecutive passes that placed nothing and left the outstanding count where it was. */
    private static int noProgressRounds;

    /** Outstanding count from the previous pass, so passes can be compared. */
    private static int lastOutstanding = Integer.MAX_VALUE;

    private SchematicController() {
    }

    // --- loading ---

    /**
     * Reads a schematic and anchors it at the player.
     *
     * <p>A failed load clears whatever was loaded before. Keeping the previous build running
     * after the user asked for a different file would place the wrong schematic, which is
     * worse than placing nothing.
     *
     * @throws IOException if the file cannot be read; the message is also kept for {@link #error}
     */
    public static void load(Path path, Minecraft mc) throws IOException {
        try {
            SchematicReader.Result result = SchematicReader.load(path);

            file = path;
            name = result.name();
            format = result.format();
            size = result.size();
            total = result.placements().size();
            skipped = result.skipped();
            regions = result.regions();
            dropped = result.dropped();
            relative = List.copyOf(result.placements());
            loaded = true;
            error = null;

            anchorHere(mc);
        } catch (IOException e) {
            clearState();
            error = e.getMessage() == null ? e.toString() : e.getMessage();
            throw e;
        }
    }

    /** Drops the loaded schematic and any plan built from it. */
    public static void unload() {
        clearState();
        error = null;
    }

    /**
     * Puts the schematic's origin at the player, plus the configured offset.
     *
     * <p>Offsets exist because the useful default is never zero: a base is built beside or
     * below the player, not inside them.
     */
    public static void anchorHere(Minecraft mc) {
        if (mc == null || mc.player == null) {
            anchor = null;
            absolute = List.of();
            materialCache = null;
            needsReplan = true;
            state = State.IDLE;
            return;
        }

        LocalPlayer player = mc.player;
        BlockPos base = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        anchor = base.offset(Toggles.schematicOffsetX, Toggles.schematicOffsetY, Toggles.schematicOffsetZ);
        absolute = anchored(relative, anchor);
        materialCache = null;
        EXECUTOR.reset();
        needsReplan = true;
        noProgressRounds = 0;
        lastOutstanding = Integer.MAX_VALUE;
        stallReason = "";
        state = State.IDLE;
    }

    /**
     * Drops everything that is tied to a position in a world.
     *
     * <p>Wired to the client's level-change reset. The plan holds absolute block coordinates,
     * so carrying it into another dimension would have the executor clicking positions from
     * the old world. The file stays loaded — it is still what the user picked — but the
     * anchor has to be set again.
     */
    public static void reset() {
        EXECUTOR.reset();
        absolute = List.of();
        anchor = null;
        materialCache = null;
        needsReplan = false;
        signatureValid = false;
        noProgressRounds = 0;
        lastOutstanding = Integer.MAX_VALUE;
        stallReason = "";
        state = loaded ? State.IDLE : State.NONE;
    }

    private static void clearState() {
        loaded = false;
        file = null;
        name = "";
        format = "";
        size = Vec3i.ZERO;
        total = 0;
        skipped = 0;
        regions = 0;
        dropped = 0;
        relative = List.of();
        absolute = List.of();
        anchor = null;
        materialCache = null;
        EXECUTOR.reset();
        needsReplan = false;
        signatureValid = false;
        noProgressRounds = 0;
        lastOutstanding = Integer.MAX_VALUE;
        stallReason = "";
        state = State.NONE;
    }

    // --- per-tick driver ---

    /**
     * Advances the build by at most one action, rebuilding the plan whenever its inputs
     * changed.
     *
     * <p>Called once per client tick. The expensive passes — comparing the whole placement
     * list against the world, and against the hotbar — run only when something invalidated
     * the plan, never per frame.
     */
    public static void tick(Minecraft mc) {
        if (!Toggles.schematicBuilder) {
            if (EXECUTOR.isRunning()) {
                EXECUTOR.stop();
            }
            return;
        }
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        if (!mc.mouseHandler.isMouseGrabbed()) {
            // A screen owns the keyboard, the mouse and the crosshair, so walking or clicking
            // behind one is not something a player can do — and the browser screen is exactly
            // where the user goes to set a build up. Minecraft's own screen field is private
            // in 26.3; the mouse grab is the same state seen from the other end, and it also
            // covers the window losing focus.
            EXECUTOR.release();
            return;
        }
        if (!loaded || anchor == null) {
            return;
        }

        if (hotbarChanged(mc.player)) {
            needsReplan = true;
        }

        if (needsReplan) {
            needsReplan = false;
            replan(mc, mc.player);
        }

        if (EXECUTOR.isRunning()) {
            SchematicExecutor.Outcome outcome = EXECUTOR.tick(mc);
            if (EXECUTOR.isRunning()) {
                state = outcome == SchematicExecutor.Outcome.WALKING ? State.WALKING : State.RUNNING;
            } else {
                // The pass ended: every queued action is done, or the executor gave up on
                // the ones it could not finish. Either way the whole file has to be re-checked
                // against the world before claiming the build is complete, so re-plan once.
                needsReplan = true;
            }
        }
    }

    /**
     * Rebuilds the plan from everything still outstanding.
     *
     * <p>The check runs against the whole file rather than the executor's queue, because the
     * queue only ever held the part of the build the hotbar could service.
     */
    private static void replan(Minecraft mc, LocalPlayer player) {
        List<SchematicBuilder.Placement> todo = outstanding(mc.level, absolute);
        int placedLastPass = EXECUTOR.placed();
        int outstandingBefore = lastOutstanding;
        lastOutstanding = todo.size();

        if (todo.isEmpty()) {
            EXECUTOR.stop();
            EXECUTOR.builder().plan(List.of(), null, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
            state = State.COMPLETE;
            stallReason = "";
            noProgressRounds = 0;
            return;
        }

        HumanizedBuilder.HotbarLookup lookup =
                HumanizedBuilder.ofHotbar(SchematicExecutor.hotbar(player));
        int planned = EXECUTOR.builder().plan(todo, lookup,
                player.getX(), player.getEyeY(), player.getZ(),
                player.getYRot(), player.getXRot());

        if (planned == 0) {
            // Positions remain but none of them can be built from what is carried. Reporting
            // that is the point: the alternative is a builder that silently does nothing.
            EXECUTOR.stop();
            state = State.STALLED;
            stallReason = todo.size() + " left, no material for them";
            return;
        }

        // A pass that placed nothing and left the outstanding count where it was cannot
        // proceed, however many actions it planned. Without this check the build walks back
        // and forth over positions it cannot reach for as long as the module is on, which is
        // indistinguishable from work.
        if (placedLastPass == 0 && todo.size() >= outstandingBefore) {
            if (++noProgressRounds >= MAX_NO_PROGRESS_ROUNDS) {
                EXECUTOR.stop();
                state = State.STALLED;
                stallReason = reason(EXECUTOR.blocked(), todo.size());
                return;
            }
        } else {
            noProgressRounds = 0;
        }

        EXECUTOR.start();
        state = State.RUNNING;
        stallReason = "";
    }

    /**
     * Turns the executor's reason for giving up into something worth reading.
     *
     * <p>The count is the useful part of it: four blocks out of reach is a different problem
     * from four thousand.
     */
    private static String reason(SchematicExecutor.Blocked blocked, int left) {
        String detail = switch (blocked) {
            case NO_MATERIAL -> "no material";
            case UNREACHABLE -> "out of reach";
            case OCCUPIED -> "something else is standing there";
            case NO_SURFACE -> "nothing beside them to build against";
            case NONE -> "the last pass placed none of them";
        };
        return left + " left, " + detail;
    }

    /** True when the set of items in the hotbar differs from the one the plan was built for. */
    private static boolean hotbarChanged(LocalPlayer player) {
        boolean changed = false;
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            Item item = stack == null || stack.isEmpty() ? null : stack.getItem();
            if (!signatureValid || hotbarSignature[slot] != item) {
                changed = true;
            }
            hotbarSignature[slot] = item;
        }
        signatureValid = true;
        return changed;
    }

    // --- pure helpers ---

    /** Shifts schematic-relative placements onto a world anchor. */
    public static List<SchematicBuilder.Placement> anchored(
            List<SchematicBuilder.Placement> relative, BlockPos anchor) {
        if (anchor == null || relative == null || relative.isEmpty()) {
            return relative == null ? List.of() : List.copyOf(relative);
        }

        List<SchematicBuilder.Placement> out = new ArrayList<>(relative.size());
        for (SchematicBuilder.Placement placement : relative) {
            out.add(new SchematicBuilder.Placement(placement.pos().offset(anchor), placement.state()));
        }
        return List.copyOf(out);
    }

    /** The placements the world does not already satisfy. */
    public static List<SchematicBuilder.Placement> outstanding(
            Level level, List<SchematicBuilder.Placement> absolute) {
        if (level == null || absolute == null || absolute.isEmpty()) {
            return List.of();
        }

        List<SchematicBuilder.Placement> out = new ArrayList<>();
        for (SchematicBuilder.Placement placement : absolute) {
            if (!HumanizedBuilder.placed(level, placement.pos(), placement.state())) {
                out.add(placement);
            }
        }
        return out;
    }

    /**
     * Totals the blocks a build needs, largest first.
     *
     * @param slotFor hotbar slot holding an item, or a negative number when it is not carried
     */
    public static List<Material> materials(
            List<SchematicBuilder.Placement> placements, ToIntFunction<Item> slotFor) {
        if (placements == null || placements.isEmpty()) {
            return List.of();
        }

        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (SchematicBuilder.Placement placement : placements) {
            needed.merge(SchematicPlacer.itemFor(placement.state()), 1, Integer::sum);
        }

        List<Material> out = new ArrayList<>(needed.size());
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            boolean carried = slotFor != null && slotFor.applyAsInt(entry.getKey()) >= 0;
            out.add(new Material(entry.getKey(), entry.getValue(), carried ? 1 : 0));
        }
        out.sort(Comparator.comparingInt(Material::needed).reversed());
        return out;
    }

    // --- state ---

    public static boolean isLoaded() {
        return loaded;
    }

    public static String fileName() {
        return file == null ? "" : file.getFileName().toString();
    }

    public static String name() {
        return name;
    }

    public static String format() {
        return format;
    }

    public static Vec3i size() {
        return size;
    }

    /** The last load failure, or null. Kept after a failed load so the UI can explain it. */
    public static String error() {
        return error;
    }

    /** Placements in the file. */
    public static int total() {
        return total;
    }

    /** Blocks the file held beyond the cap, which will not be built. */
    public static int truncated() {
        return dropped;
    }

    /** Palette entries naming a block this client does not have. */
    public static int skipped() {
        return skipped;
    }

    /** Sub-regions in the file; 1 for sponge. */
    public static int regions() {
        return regions;
    }

    /** Actions in the current plan. */
    public static int queued() {
        return EXECUTOR.builder().planned();
    }

    /** Actions left in the current pass, which is the number the HUD means by "remaining". */
    public static int remaining() {
        return EXECUTOR.builder().remaining();
    }

    public static BlockPos anchor() {
        return anchor;
    }

    public static State state() {
        return state;
    }

    /** Why the build is not moving, or "" when it is. */
    public static String stallReason() {
        return stallReason;
    }

    /**
     * Blocks that were placed in the wrong state, because the state the schematic wants
     * cannot be produced by placing that block here.
     *
     * <p>Reported rather than hidden: these are the places the finished build will not match
     * the file, and they are almost always a property the world decides — water logging, a
     * fence connecting to what is beside it — rather than something the builder got wrong.
     */
    public static int approximate() {
        return EXECUTOR.approximate();
    }

    /** Materials for the loaded schematic against the live hotbar, cached between changes. */
    public static List<Material> materials() {
        if (materialCache == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                return List.of();
            }
            LocalPlayer player = mc.player;
            materialCache = List.copyOf(materials(absolute, item -> hotbarSlotFor(player, item)));
        }
        return materialCache;
    }

    private static int hotbarSlotFor(LocalPlayer player, Item item) {
        if (item == null) {
            return -1;
        }
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && !stack.isEmpty() && stack.getItem() == item) {
                return slot;
            }
        }
        return -1;
    }
}
