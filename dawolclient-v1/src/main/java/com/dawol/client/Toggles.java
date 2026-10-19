package com.dawol.client;

/**
 * Runtime-toggleable feature flags. Mutated only from the client thread.
 *
 * <p>Defaults are deliberately conservative. Anything that produces an unmistakable
 * on-screen signature â€” through-wall geometry, tracers, floating health bars and labels,
 * and automated combat â€” ships <b>disabled</b> and is opt-in. What is on by default is the
 * passive side: entity tracking that only draws what is already visible, and the trace
 * scan, which is pure client-side block reading.
 */
public final class Toggles {

    // --- ESP ---
    public static boolean espPlayers = true;
    public static boolean espMobs = false;
    public static boolean espItems = false;

    /**
     * Draw geometry through terrain. This is the single most recognisable feature in the
     * mod, so it defaults off: with it off, boxes only appear on entities you could
     * already see, which reads as a normal outline mod.
     */
    public static boolean espThroughWalls = false;

    public static boolean espTracers = true;
    public static boolean espHealthBar = false;
    public static boolean espLabels = false;
    public static float espRange = 128.0F;
    /**
     * Cap on tracer lines drawn per frame, nearest first. Without a cap a crowded area
     * produces an unreadable fan and wastes frame time on lines nobody can follow.
     */
    public static int maxTracers = 32;
    /**
     * Draw tracers over terrain. Kept separate from the box setting because a tracer that is
     * depth-tested is hidden by every wall, which makes the feature look broken.
     */
    public static boolean tracersOnTop = true;

    // --- Block ESP ---
    /**
     * Master switch for per-block ESP. The selection itself lives in
     * {@link BlockSelection}; tracers are always drawn to selected blocks, so there is
     * deliberately no tracer toggle for this feature.
     */
    public static boolean blockEsp = false;
    public static boolean blockEspThroughWalls = false;
    public static float blockEspRange = 64.0F;

    // --- Stash finder ---
    // One configurable block scanner replaces the previous tangle of separate ore,
    // container, spawner and base settings. Everything it finds is scored per chunk, and
    // the same data drives both the stash list and the chunk overlay.
    public static boolean stashFinder = false;
    public static boolean stashThroughWalls = true;
    public static boolean stashShowAllChunks = false;
    public static float stashRange = 256.0F;
    /** Containers in a chunk required before it counts as a stash. */
    public static int stashMinContainers = 3;
    /** Combined score required before a chunk counts as a stash. */
    public static double stashMinScore = 9.0D;
    /** Scan radius in chunks for a manual re-scan. */
    public static int scanRadiusChunks = 8;

    // --- Combat ---
    /** Automated attacking is off by default; the gates below still apply when enabled. */
    public static boolean combatAssist = false;
    public static boolean combatSilentRotate = true;
    public static boolean combatReachGate = true;
    /** Hard cap on attack reach in blocks. Server check thresholds sit at 3.0 for vanilla. */
    public static double combatMaxReach = 3.0D;
    public static boolean combatLineOfSightGate = true;
    public static boolean combatCooldownGate = true;

    // --- Fullbright ---
    /** Lifts the brightness ceiling using the public gamma option. No mixin involved. */
    public static boolean fullbright = false;
    /** Requested gamma; the module clamps it to the [0, 1] the option accepts. */
    public static double fullbrightGamma = 1.0D;
    /** Previous gamma, restored when Fullbright is switched off. */
    public static double savedGamma = 1.0D;

    // --- Anti-trap ---
    /** Hides armour stands, which are the usual cage a trap uses. */
    public static boolean antiTrap = false;

    // --- New chunks ---
        /** Chunk-level overlay: the "where is worth going" view. */
    public static boolean chunkEsp = false;
    /** Highlight chunks first received recently, the classic new-chunks base signal. */
    public static boolean chunkNewHighlight = true;
    /** How recent counts as new, in milliseconds. */
    public static long chunkNewWindowMs = 120_000L;
    /** Highlight chunks holding an unusual ore concentration. */
    public static boolean chunkDensityHighlight = true;
    /** Ore count in a chunk above which it is treated as dense. */
    public static int chunkDensityThreshold = 24;

    // --- Player modules ---
    public static boolean autoReconnect = false;
    public static boolean antiKick = false;
    public static boolean autoRespawn = false;
    public static boolean autoEat = false;
    /** Hunger level at which Auto Eat acts. */
    public static int autoEatThreshold = 14;

    // --- Misc ---
    public static boolean schematicBuilder = false;
    public static boolean statusHud = true;

    /**
     * Where the schematic's origin lands relative to the player when it is anchored.
     *
     * <p>The useful default is never zero: a build goes beside or below the player, not
     * through them. These are the first thing to set after loading a file.
     */
    public static int schematicOffsetX = 0;
    public static int schematicOffsetY = 0;
    public static int schematicOffsetZ = 0;

    /** Walk to placements that are out of reach instead of skipping them. */
    public static boolean schematicWalk = true;

    /**
     * Break a block already occupying a schematic position before placing the intended one,
     * the way Litematica's replace mode does.
     */
    public static boolean schematicReplace = true;

    // --- Timing randomisation ---
    /**
     * Every automated action draws its delay from this window instead of using a fixed
     * interval. A constant interval is a trivial timing fingerprint.
     */
    public static long autoDelayMinMs = 250L;
    public static long autoDelayMaxMs = 650L;
    /** Add a random offset to the reach distance used for gating, in millimetres. */
    public static long reachJitterMinMm = 0L;
    public static long reachJitterMaxMm = 60L;
    /** Add a random offset to aim settling, in milli-degrees. */
    public static long rotationJitterMinMilliDeg = 0L;
    public static long rotationJitterMaxMilliDeg = 400L;

    // --- Schematic builder humanisation ---
    /** Pause between placements, drawn fresh each time. */
    public static long buildDelayMinMs = 180L;
    public static long buildDelayMaxMs = 520L;
    /** Chance per placement of an attention-lapse pause, and the pause window. */
    public static double buildPauseChancePercent = 6.0D;
    public static long buildPauseMinMs = 900L;
    public static long buildPauseMaxMs = 2600L;
    /** Chance per placement of a deliberate, recoverable mistake. */
    public static double buildMistakeChancePercent = 8.0D;
    /** Chance per placement of a verifying glance before continuing. */
    public static double buildDoubleCheckChancePercent = 12.0D;

    // --- Corz-derived ---
    /** Keep a totem in the offhand (Corz: Auto Totem). */
    public static boolean autoTotem = false;
    /** Show carried totem count on the HUD (Corz: Totem Counter). */
    public static boolean totemCounter = true;
    /** Highlight the current combat target (Corz: Target ESP). */
    public static boolean targetEsp = true;
    /** Auto-steal items from open containers. */
    public static boolean chestStealer = false;
    /** Auto-sprint when food is sufficient. */
    public static boolean autoSprint = false;

    // --- PVP ---
    /** Also target players (not just mobs) when Combat Assist is on. */
    public static boolean pvpTargetPlayers = false;

    // --- Mixin-backed ---
    /** Cancel incoming knockback on the local player. */
    public static boolean velocityCancel = false;
    /** Cancel fall damage on the local player. */
    public static boolean noFall = false;

    // --- Base finder ---
    public static boolean baseFinder = false;
    public static boolean baseThroughWalls = true;
    public static float baseRange = 256.0F;
    /** Structural-density score required before a chunk counts as a base. */
    public static double baseMinScore = 50.0D;

    // --- Zoom (Krypton: GameRendererMixin) ---
    /** Reduce FOV when zoom is active. */
    public static boolean zoom = false;
    /** FOV divisor. 4 = quarter FOV (~22° from 90°). */
    public static double zoomFactor = 4.0;

    private Toggles() {
    }

    /** True when any world-geometry feature is producing output. Used by the HUD. */
    public static boolean anyEspActive() {
        return espPlayers || espMobs || espItems;
    }

    /**
     * True when the current configuration would draw something a bystander would call
     * blatant: through-wall geometry, tracers, or automated combat.
     */
    public static boolean anyBlatantFeatureActive() {
        return (espThroughWalls && anyEspActive())
                || (espTracers && anyEspActive())
                || (espHealthBar && espPlayers)
                || (stashThroughWalls && stashFinder)
                || combatAssist;
    }
}

