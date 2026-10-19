package com.dawol.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Flat properties persistence for feature toggles.
 *
 * <p>Only the durable user preferences are stored; runtime scan results are held by
 * and are intentionally not persisted across sessions.
 */
public final class Config {

    private static final String FILE_NAME = "dawolclient.properties";

    private Config() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            DawolClient.LOGGER.warn("[dawol] could not read {}: {}", path, e.toString());
            return;
        }

        Toggles.espPlayers = bool(props, "esp.players", Toggles.espPlayers);
        Toggles.espMobs = bool(props, "esp.mobs", Toggles.espMobs);
        Toggles.espItems = bool(props, "esp.items", Toggles.espItems);
        Toggles.espThroughWalls = bool(props, "esp.throughWalls", Toggles.espThroughWalls);
        Toggles.espTracers = bool(props, "esp.tracers", Toggles.espTracers);
        Toggles.espHealthBar = bool(props, "esp.healthBar", Toggles.espHealthBar);
        Toggles.espLabels = bool(props, "esp.labels", Toggles.espLabels);
        Toggles.espRange = floatOf(props, "esp.range", Toggles.espRange);
        Toggles.maxTracers = intOf(props, "esp.maxTracers", Toggles.maxTracers);
        Toggles.tracersOnTop = bool(props, "esp.tracersOnTop", Toggles.tracersOnTop);

        Toggles.buildDelayMinMs = longOf(props, "build.delayMinMs", Toggles.buildDelayMinMs);
        Toggles.buildDelayMaxMs = longOf(props, "build.delayMaxMs", Toggles.buildDelayMaxMs);
        Toggles.buildPauseChancePercent =
                doubleOf(props, "build.pauseChancePercent", Toggles.buildPauseChancePercent);
        Toggles.buildMistakeChancePercent =
                doubleOf(props, "build.mistakeChancePercent", Toggles.buildMistakeChancePercent);
        Toggles.buildDoubleCheckChancePercent =
                doubleOf(props, "build.doubleCheckChancePercent", Toggles.buildDoubleCheckChancePercent);

        Toggles.autoDelayMinMs = longOf(props, "auto.delayMinMs", Toggles.autoDelayMinMs);
        Toggles.autoDelayMaxMs = longOf(props, "auto.delayMaxMs", Toggles.autoDelayMaxMs);
        Toggles.reachJitterMaxMm = longOf(props, "auto.reachJitterMaxMm", Toggles.reachJitterMaxMm);
        Toggles.rotationJitterMaxMilliDeg =
                longOf(props, "auto.rotationJitterMaxMilliDeg", Toggles.rotationJitterMaxMilliDeg);

        Toggles.blockEsp = bool(props, "blockEsp.enabled", Toggles.blockEsp);
        Toggles.blockEspThroughWalls = bool(props, "blockEsp.throughWalls", Toggles.blockEspThroughWalls);
        Toggles.blockEspRange = floatOf(props, "blockEsp.range", Toggles.blockEspRange);

        // Block selection is stored as a newline-joined list of registry ids under one key.
        String blocks = props.getProperty("blockEsp.blocks", "");
        BlockSelection.clear();
        if (!blocks.isBlank()) {
            for (String id : blocks.split(",")) {
                String trimmed = id.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                var block = BlockSelection.byId(trimmed);
                if (block != null) {
                    BlockSelection.select(block);
                }
            }
        }
        DawolClient.LOGGER.info("[dawol] restored {} selected blocks", BlockSelection.size());

        Toggles.stashFinder = bool(props, "stash.enabled", Toggles.stashFinder);
        Toggles.stashThroughWalls = bool(props, "stash.throughWalls", Toggles.stashThroughWalls);
        Toggles.stashShowAllChunks = bool(props, "stash.showAllChunks", Toggles.stashShowAllChunks);
        Toggles.stashRange = floatOf(props, "stash.range", Toggles.stashRange);
        Toggles.stashMinContainers = intOf(props, "stash.minContainers", Toggles.stashMinContainers);
        Toggles.stashMinScore = doubleOf(props, "stash.minScore", Toggles.stashMinScore);
        Toggles.scanRadiusChunks = intOf(props, "stash.scanRadiusChunks", Toggles.scanRadiusChunks);

        Toggles.pvpTargetPlayers = bool(props, "combat.pvpTargetPlayers", Toggles.pvpTargetPlayers);
        Toggles.velocityCancel = bool(props, "combat.velocityCancel", Toggles.velocityCancel);
        Toggles.noFall = bool(props, "combat.noFall", Toggles.noFall);
        Toggles.combatAssist = bool(props, "combat.assist", Toggles.combatAssist);
        Toggles.combatSilentRotate = bool(props, "combat.silentRotate", Toggles.combatSilentRotate);
        Toggles.combatReachGate = bool(props, "combat.reachGate", Toggles.combatReachGate);
        Toggles.combatMaxReach = doubleOf(props, "combat.maxReach", Toggles.combatMaxReach);
        Toggles.combatLineOfSightGate = bool(props, "combat.lineOfSightGate", Toggles.combatLineOfSightGate);
        Toggles.combatCooldownGate = bool(props, "combat.cooldownGate", Toggles.combatCooldownGate);
        Toggles.targetEsp = bool(props, "combat.targetEsp", Toggles.targetEsp);

        Toggles.fullbright = bool(props, "fullbright.enabled", Toggles.fullbright);
        Toggles.antiTrap = bool(props, "antiTrap.enabled", Toggles.antiTrap);

        Toggles.autoReconnect = bool(props, "player.autoReconnect", Toggles.autoReconnect);
        Toggles.antiKick = bool(props, "player.antiKick", Toggles.antiKick);
        Toggles.autoRespawn = bool(props, "player.autoRespawn", Toggles.autoRespawn);
        Toggles.autoEat = bool(props, "player.autoEat", Toggles.autoEat);
        Toggles.autoEatThreshold = intOf(props, "player.autoEatThreshold", Toggles.autoEatThreshold);
        Toggles.autoTotem = bool(props, "player.autoTotem", Toggles.autoTotem);
        Toggles.totemCounter = bool(props, "player.totemCounter", Toggles.totemCounter);
        Toggles.autoSprint = bool(props, "player.autoSprint", Toggles.autoSprint);
        Toggles.chestStealer = bool(props, "player.chestStealer", Toggles.chestStealer);

        Toggles.baseFinder = bool(props, "base.enabled", Toggles.baseFinder);
        Toggles.baseThroughWalls = bool(props, "base.throughWalls", Toggles.baseThroughWalls);
        Toggles.baseRange = floatOf(props, "base.range", Toggles.baseRange);
        Toggles.baseMinScore = doubleOf(props, "base.minScore", Toggles.baseMinScore);

        Toggles.chunkEsp = bool(props, "chunk.enabled", Toggles.chunkEsp);
        Toggles.chunkNewHighlight = bool(props, "chunk.newHighlight", Toggles.chunkNewHighlight);
        Toggles.chunkNewWindowMs = longOf(props, "chunk.newWindowMs", Toggles.chunkNewWindowMs);
        Toggles.chunkDensityHighlight = bool(props, "chunk.densityHighlight", Toggles.chunkDensityHighlight);
        Toggles.chunkDensityThreshold = intOf(props, "chunk.densityThreshold", Toggles.chunkDensityThreshold);

        Toggles.zoom = bool(props, "zoom.enabled", Toggles.zoom);
        Toggles.zoomFactor = doubleOf(props, "zoom.factor", Toggles.zoomFactor);

        Toggles.schematicBuilder = bool(props, "schematicBuilder.enabled", Toggles.schematicBuilder);
        Toggles.schematicWalk = bool(props, "schematicBuilder.walk", Toggles.schematicWalk);
        Toggles.schematicReplace = bool(props, "schematicBuilder.replace", Toggles.schematicReplace);
        Toggles.schematicOffsetX = intOf(props, "schematicBuilder.offsetX", Toggles.schematicOffsetX);
        Toggles.schematicOffsetY = intOf(props, "schematicBuilder.offsetY", Toggles.schematicOffsetY);
        Toggles.schematicOffsetZ = intOf(props, "schematicBuilder.offsetZ", Toggles.schematicOffsetZ);
        Toggles.statusHud = bool(props, "statusHud.enabled", Toggles.statusHud);

        DawolClient.LOGGER.info("[dawol] config loaded from {}", path);
    }

    public static void save() {
        Properties props = new Properties();

        props.setProperty("esp.players", Boolean.toString(Toggles.espPlayers));
        props.setProperty("esp.mobs", Boolean.toString(Toggles.espMobs));
        props.setProperty("esp.items", Boolean.toString(Toggles.espItems));
        props.setProperty("esp.throughWalls", Boolean.toString(Toggles.espThroughWalls));
        props.setProperty("esp.tracers", Boolean.toString(Toggles.espTracers));
        props.setProperty("esp.healthBar", Boolean.toString(Toggles.espHealthBar));
        props.setProperty("esp.labels", Boolean.toString(Toggles.espLabels));
        props.setProperty("esp.range", Float.toString(Toggles.espRange));
        props.setProperty("esp.maxTracers", Integer.toString(Toggles.maxTracers));
        props.setProperty("esp.tracersOnTop", Boolean.toString(Toggles.tracersOnTop));

        props.setProperty("build.delayMinMs", Long.toString(Toggles.buildDelayMinMs));
        props.setProperty("build.delayMaxMs", Long.toString(Toggles.buildDelayMaxMs));
        props.setProperty("build.pauseChancePercent", Double.toString(Toggles.buildPauseChancePercent));
        props.setProperty("build.mistakeChancePercent", Double.toString(Toggles.buildMistakeChancePercent));
        props.setProperty("build.doubleCheckChancePercent",
                Double.toString(Toggles.buildDoubleCheckChancePercent));

        props.setProperty("auto.delayMinMs", Long.toString(Toggles.autoDelayMinMs));
        props.setProperty("auto.delayMaxMs", Long.toString(Toggles.autoDelayMaxMs));
        props.setProperty("auto.reachJitterMaxMm", Long.toString(Toggles.reachJitterMaxMm));
        props.setProperty("auto.rotationJitterMaxMilliDeg", Long.toString(Toggles.rotationJitterMaxMilliDeg));

        props.setProperty("blockEsp.enabled", Boolean.toString(Toggles.blockEsp));
        props.setProperty("blockEsp.throughWalls", Boolean.toString(Toggles.blockEspThroughWalls));
        props.setProperty("blockEsp.range", Float.toString(Toggles.blockEspRange));
        props.setProperty("blockEsp.blocks", String.join(",", BlockSelection.selectedIds()));

        props.setProperty("stash.enabled", Boolean.toString(Toggles.stashFinder));
        props.setProperty("stash.throughWalls", Boolean.toString(Toggles.stashThroughWalls));
        props.setProperty("stash.showAllChunks", Boolean.toString(Toggles.stashShowAllChunks));
        props.setProperty("stash.range", Float.toString(Toggles.stashRange));
        props.setProperty("stash.minContainers", Integer.toString(Toggles.stashMinContainers));
        props.setProperty("stash.minScore", Double.toString(Toggles.stashMinScore));
        props.setProperty("stash.scanRadiusChunks", Integer.toString(Toggles.scanRadiusChunks));

        props.setProperty("combat.pvpTargetPlayers", Boolean.toString(Toggles.pvpTargetPlayers));
        props.setProperty("combat.velocityCancel", Boolean.toString(Toggles.velocityCancel));
        props.setProperty("combat.noFall", Boolean.toString(Toggles.noFall));
        props.setProperty("combat.assist", Boolean.toString(Toggles.combatAssist));
        props.setProperty("combat.silentRotate", Boolean.toString(Toggles.combatSilentRotate));
        props.setProperty("combat.reachGate", Boolean.toString(Toggles.combatReachGate));
        props.setProperty("combat.maxReach", Double.toString(Toggles.combatMaxReach));
        props.setProperty("combat.lineOfSightGate", Boolean.toString(Toggles.combatLineOfSightGate));
        props.setProperty("combat.cooldownGate", Boolean.toString(Toggles.combatCooldownGate));
        props.setProperty("combat.targetEsp", Boolean.toString(Toggles.targetEsp));

        props.setProperty("fullbright.enabled", Boolean.toString(Toggles.fullbright));
        props.setProperty("antiTrap.enabled", Boolean.toString(Toggles.antiTrap));

        props.setProperty("player.autoReconnect", Boolean.toString(Toggles.autoReconnect));
        props.setProperty("player.antiKick", Boolean.toString(Toggles.antiKick));
        props.setProperty("player.autoRespawn", Boolean.toString(Toggles.autoRespawn));
        props.setProperty("player.autoEat", Boolean.toString(Toggles.autoEat));
        props.setProperty("player.autoEatThreshold", Integer.toString(Toggles.autoEatThreshold));
        props.setProperty("player.autoTotem", Boolean.toString(Toggles.autoTotem));
        props.setProperty("player.totemCounter", Boolean.toString(Toggles.totemCounter));
        props.setProperty("player.autoSprint", Boolean.toString(Toggles.autoSprint));
        props.setProperty("player.chestStealer", Boolean.toString(Toggles.chestStealer));

        props.setProperty("base.enabled", Boolean.toString(Toggles.baseFinder));
        props.setProperty("base.throughWalls", Boolean.toString(Toggles.baseThroughWalls));
        props.setProperty("base.range", Float.toString(Toggles.baseRange));
        props.setProperty("base.minScore", Double.toString(Toggles.baseMinScore));

        props.setProperty("chunk.enabled", Boolean.toString(Toggles.chunkEsp));
        props.setProperty("chunk.newHighlight", Boolean.toString(Toggles.chunkNewHighlight));
        props.setProperty("chunk.newWindowMs", Long.toString(Toggles.chunkNewWindowMs));
        props.setProperty("chunk.densityHighlight", Boolean.toString(Toggles.chunkDensityHighlight));
        props.setProperty("chunk.densityThreshold", Integer.toString(Toggles.chunkDensityThreshold));

        props.setProperty("zoom.enabled", Boolean.toString(Toggles.zoom));
        props.setProperty("zoom.factor", Double.toString(Toggles.zoomFactor));

        props.setProperty("schematicBuilder.enabled", Boolean.toString(Toggles.schematicBuilder));
        props.setProperty("schematicBuilder.walk", Boolean.toString(Toggles.schematicWalk));
        props.setProperty("schematicBuilder.replace", Boolean.toString(Toggles.schematicReplace));
        props.setProperty("schematicBuilder.offsetX", Integer.toString(Toggles.schematicOffsetX));
        props.setProperty("schematicBuilder.offsetY", Integer.toString(Toggles.schematicOffsetY));
        props.setProperty("schematicBuilder.offsetZ", Integer.toString(Toggles.schematicOffsetZ));
        props.setProperty("statusHud.enabled", Boolean.toString(Toggles.statusHud));

        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "DawolClient configuration");
            }
        } catch (IOException e) {
            DawolClient.LOGGER.warn("[dawol] could not write {}: {}", path, e.toString());
        }
    }

    private static long longOf(Properties props, String key, long fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean bool(Properties props, String key, boolean fallback) {
        String value = props.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int intOf(Properties props, String key, int fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float floatOf(Properties props, String key, float fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleOf(Properties props, String key, double fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
