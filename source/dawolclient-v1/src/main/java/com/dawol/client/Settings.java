package com.dawol.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of every user-facing setting, in display order.
 *
 * <p>The settings screen renders this list generically, so a new toggle only needs an
 * entry here.
 */
public final class Settings {

    private Settings() {
    }

    public static List<Setting> all() {
        List<Setting> out = new ArrayList<>();

        // --- Entity ESP ---
        out.add(Setting.bool("ESP: players", "Boxes around other players",
                () -> Toggles.espPlayers, v -> Toggles.espPlayers = v));
        out.add(Setting.bool("ESP: mobs", "Boxes around hostile mobs",
                () -> Toggles.espMobs, v -> Toggles.espMobs = v));
        out.add(Setting.bool("ESP: items", "Boxes around dropped items",
                () -> Toggles.espItems, v -> Toggles.espItems = v));
        out.add(Setting.bool("ESP: through walls", "Entity boxes draw over terrain",
                () -> Toggles.espThroughWalls, v -> Toggles.espThroughWalls = v));
        out.add(Setting.bool("ESP: tracers", "Line from your eye to each entity",
                () -> Toggles.espTracers, v -> Toggles.espTracers = v));
        out.add(Setting.bool("ESP: health bars", "Health bar above living entities",
                () -> Toggles.espHealthBar, v -> Toggles.espHealthBar = v));
        out.add(Setting.bool("ESP: labels", "Name and distance text",
                () -> Toggles.espLabels, v -> Toggles.espLabels = v));
        out.add(Setting.number("ESP: range", "Maximum entity distance in blocks",
                8.0D, 16.0D, 512.0D,
                () -> Toggles.espRange,
                v -> Toggles.espRange = (float) v,
                v -> String.format("%.0f m", v)));

        // --- Block ESP ---
        // Tracers are always drawn for block ESP, so there is deliberately no toggle.
        out.add(Setting.bool("Block ESP: on", "Draw selected blocks; tracers always on",
                () -> Toggles.blockEsp, v -> Toggles.blockEsp = v));
        out.add(Setting.bool("Block ESP: through walls", "Selected blocks draw over terrain",
                () -> Toggles.blockEspThroughWalls, v -> Toggles.blockEspThroughWalls = v));
        out.add(Setting.number("Block ESP: range", "Maximum block distance in blocks",
                8.0D, 16.0D, 256.0D,
                () -> Toggles.blockEspRange,
                v -> Toggles.blockEspRange = (float) v,
                v -> String.format("%.0f m", v)));

        // --- Stash finder ---
        out.add(Setting.bool("Stash: on", "Score chunks for container clusters",
                () -> Toggles.stashFinder, v -> Toggles.stashFinder = v));
        out.add(Setting.bool("Stash: through walls", "Stash markers draw over terrain",
                () -> Toggles.stashThroughWalls, v -> Toggles.stashThroughWalls = v));
        out.add(Setting.bool("Stash: show all chunks", "Show chunks below the stash threshold too",
                () -> Toggles.stashShowAllChunks, v -> Toggles.stashShowAllChunks = v));
        out.add(Setting.numberLong("Stash: range", "Maximum distance to draw a stash in blocks",
                16L, 16L, 512L,
                () -> (long) Toggles.stashRange,
                v -> Toggles.stashRange = (float) v,
                v -> v + " m"));
        out.add(Setting.numberLong("Stash: min containers", "Containers needed before a chunk counts",
                1L, 1L, 32L,
                () -> (long) Toggles.stashMinContainers,
                v -> Toggles.stashMinContainers = (int) v,
                v -> Long.toString(v)));
        out.add(Setting.number("Stash: min score", "Combined score needed before it counts",
                1.0D, 0.0D, 200.0D,
                () -> Toggles.stashMinScore,
                v -> Toggles.stashMinScore = v,
                v -> String.format("%.0f", v)));
        out.add(Setting.numberLong("Stash: scan radius", "Chunks scanned on a manual re-scan",
                1L, 1L, 24L,
                () -> (long) Toggles.scanRadiusChunks,
                v -> Toggles.scanRadiusChunks = (int) v,
                v -> v + " chunks"));
        // --- Schematic builder humanisation ---
        // These control how the builder paces itself and how often it slips. Raising the
        // mistake chance makes a build look more human at the cost of taking longer.
        out.add(Setting.bool("ESP: tracers on top", "Draw tracer lines over terrain",
                () -> Toggles.tracersOnTop, v -> Toggles.tracersOnTop = v));

        out.add(Setting.numberLong("ESP: max tracers", "Cap on tracer lines drawn, nearest first",
                4L, 1L, 128L,
                () -> (long) Toggles.maxTracers,
                v -> Toggles.maxTracers = (int) v,
                v -> Long.toString(v)));

        out.add(Setting.bool("Build: walk to blocks", "Walk to placements that are out of reach instead of skipping them",
                () -> Toggles.schematicWalk, v -> Toggles.schematicWalk = v));
        out.add(Setting.bool("Build: replace blocks", "Break a wrong block at a schematic position before placing the right one",
                () -> Toggles.schematicReplace, v -> Toggles.schematicReplace = v));

        out.add(Setting.numberLong("Build: offset X", "Blocks east of the player to anchor the schematic",
                1L, -64L, 64L,
                () -> (long) Toggles.schematicOffsetX,
                v -> Toggles.schematicOffsetX = (int) v,
                v -> Long.toString(v)));
        out.add(Setting.numberLong("Build: offset Y", "Blocks above the player to anchor the schematic",
                1L, -64L, 64L,
                () -> (long) Toggles.schematicOffsetY,
                v -> Toggles.schematicOffsetY = (int) v,
                v -> Long.toString(v)));
        out.add(Setting.numberLong("Build: offset Z", "Blocks south of the player to anchor the schematic",
                1L, -64L, 64L,
                () -> (long) Toggles.schematicOffsetZ,
                v -> Toggles.schematicOffsetZ = (int) v,
                v -> Long.toString(v)));

        out.add(Setting.numberLong("Build: delay min", "Lower bound of the pause between placements",
                20L, 50L, 5000L,
                () -> Toggles.buildDelayMinMs, v -> Toggles.buildDelayMinMs = v,
                v -> v + " ms"));
        out.add(Setting.numberLong("Build: delay max", "Upper bound of the pause between placements",
                20L, 50L, 8000L,
                () -> Toggles.buildDelayMaxMs, v -> Toggles.buildDelayMaxMs = v,
                v -> v + " ms"));
        out.add(Setting.number("Build: mistake %", "Chance per placement of a recoverable slip",
                1.0D, 0.0D, 40.0D,
                () -> Toggles.buildMistakeChancePercent,
                v -> Toggles.buildMistakeChancePercent = v,
                v -> String.format("%.0f%%", v)));
        out.add(Setting.number("Build: pause %", "Chance per placement of a longer pause",
                1.0D, 0.0D, 40.0D,
                () -> Toggles.buildPauseChancePercent,
                v -> Toggles.buildPauseChancePercent = v,
                v -> String.format("%.0f%%", v)));

        // --- Timing randomisation ---
        // Every automated action draws from these windows. A constant interval is a
        // trivial timing fingerprint, so these are the first thing to widen if a module
        // feels mechanical.
        // (window settings follow)
        out.add(Setting.numberLong("Auto: delay min", "Lower bound of the random action delay",
                25L, 50L, 5000L,
                () -> Toggles.autoDelayMinMs,
                v -> Toggles.autoDelayMinMs = v,
                v -> v + " ms"));
        out.add(Setting.numberLong("Auto: delay max", "Upper bound of the random action delay",
                25L, 50L, 10000L,
                () -> Toggles.autoDelayMaxMs,
                v -> Toggles.autoDelayMaxMs = v,
                v -> v + " ms"));
        out.add(Setting.numberLong("Auto: reach jitter", "Random offset added to the reach cap",
                10L, 0L, 500L,
                () -> Toggles.reachJitterMaxMm,
                v -> Toggles.reachJitterMaxMm = v,
                v -> v + " mm"));
        out.add(Setting.numberLong("Auto: aim jitter", "Random offset added to the settle tolerance",
                50L, 0L, 2000L,
                () -> Toggles.rotationJitterMaxMilliDeg,
                v -> Toggles.rotationJitterMaxMilliDeg = v,
                v -> v + " mdeg"));

        // --- Combat ---
        out.add(Setting.bool("Combat: assist", "Hold attack to auto-swing at hostile mobs",
                () -> Toggles.combatAssist, v -> Toggles.combatAssist = v));
        out.add(Setting.bool("Combat: rotate", "Turn the camera toward the target",
                () -> Toggles.combatSilentRotate, v -> Toggles.combatSilentRotate = v));
        out.add(Setting.bool("Combat: reach gate", "Refuse swings beyond max reach",
                () -> Toggles.combatReachGate, v -> Toggles.combatReachGate = v));
        out.add(Setting.number("Combat: max reach", "Hard cap; vanilla checks sit at 3.0",
                0.1D, 1.0D, 6.0D,
                () -> Toggles.combatMaxReach,
                v -> Toggles.combatMaxReach = v,
                v -> String.format("%.1f blocks", v)));
        out.add(Setting.bool("Combat: line of sight", "Refuse targets with no clear path",
                () -> Toggles.combatLineOfSightGate, v -> Toggles.combatLineOfSightGate = v));
        out.add(Setting.bool("Combat: cooldown gate", "Only swing at full attack charge",
                () -> Toggles.combatCooldownGate, v -> Toggles.combatCooldownGate = v));

        return out;
    }
}
