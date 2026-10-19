package com.dawol.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint: wiring only.
 *
 * <p>Render passes hook to vanilla's gizmo collection phase ({@code BEFORE_GIZMOS}), because
 * {@code net.minecraft.gizmos.Gizmos} writes into a thread-local collector the engine
 * installs only for that phase. Submitting from anywhere else silently drops the geometry.
 *
 * <p>Chunk scanning is driven by chunk-load events rather than a per-tick sweep, so the cost
 * is paid once per chunk as it arrives instead of every frame.
 */
public final class DawolClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("dawolclient");

    private static ClientLevel lastLevel;

    @Override
    public void onInitializeClient() {
        Config.load();
        Modules.register();
        Keybinds.register();

        // Gizmo submission: entity ESP, block ESP, stash markers, combat target overlay.
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            // The camera entity is not the player and is nullable: during a camera hand-off
            // it can be absent for a frame, and TracerAnchor would NPE reading its rotation.
            // Vanilla's own Minecraft.pick null-checks it for the same reason.
            Entity cameraEntity = mc.getCameraEntity();
            if (cameraEntity == null) {
                return;
            }
            // One tracer origin for the whole frame, computed once and shared by every
            // renderer, so all tracer lines converge on exactly the same point on screen.
            Vec3 tracerOrigin = TracerAnchor.of(mc.gameRenderer.mainCamera(), cameraEntity);
            EspRenderer.render(mc, tracerOrigin);
            BlockEspRenderer.render(mc, tracerOrigin);
            StashRenderer.render(mc, Toggles.stashShowAllChunks, tracerOrigin);
            BaseFinderRenderer.render(mc, Toggles.baseThroughWalls, tracerOrigin);
            ChunkEspRenderer.render(mc, Toggles.espThroughWalls);
            CombatSystem.render(mc);
            StorageESP.render(context, tracerOrigin);
            HoleESP.render(context, tracerOrigin);
            OreSimModule.render(context, tracerOrigin);
        });

        // Unload: remove StorageESP and OreSimModule entries for unloaded chunks.
        ClientChunkEvents.CHUNK_UNLOAD.register((ClientLevel level, LevelChunk chunk) -> {
            StorageESP.removeChunk(chunk.getPos().x(), chunk.getPos().z());
            OreSimModule.removeChunk(chunk.getPos().x(), chunk.getPos().z());
        });

        // Chunk scanning for the stash finder, driven by chunk loads.
        ClientChunkEvents.CHUNK_LOAD.register((ClientLevel level, LevelChunk chunk) -> {
            try {
                if (Toggles.stashFinder) {
                    StashFinder.scan(level, chunk);
                }
                if (Toggles.baseFinder) {
                    BaseFinder.scan(level, chunk);
                }
                if (Toggles.chunkEsp) {
                    ChunkTracker.scan(level, chunk);
                }
                BlockEspRenderer.scanChunk(level, chunk);
                StorageESP.scanChunk(chunk.getPos().x(), chunk.getPos().z());
                OreSimModule.scanChunk(chunk.getPos().x(), chunk.getPos().z());
            } catch (RuntimeException e) {
                LOGGER.warn("[dawol] chunk scan failed at {}: {}", chunk.getPos(), e.toString());
            }
        });

        // Per-tick: keybinds, modules, and the schematic builder loop.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level != lastLevel) {
                lastLevel = mc.level;
                StashFinder.reset();
                BaseFinder.reset();
                ChunkTracker.reset();
                BlockEspRenderer.reset();
                CombatSystem.reset();
                // The schematic plan holds absolute block coordinates, so it must not survive
                // into a new level or the executor clicks stale positions in the new world.
                SchematicController.reset();
                StorageESP.clear();
                OreSimModule.clear();
                LOGGER.info("[dawol] level changed, stash results reset");
            }

            boolean rescan = Keybinds.poll();
            Keybinds.pollScreenKeys(mc);
            // The re-scan refills both the stash hits and the block cache, so it must run when
            // either feature is on — gating it on the stash finder alone left block ESP's
            // cache empty for anyone running it with the stash finder off.
            if (rescan && mc.level != null && (Toggles.stashFinder || Toggles.blockEsp || Toggles.baseFinder || Toggles.chunkEsp)) {
                rescanLoadedChunks(mc);
            }

            CombatSystem.tick(mc);
            FullbrightModule.tick(mc);
            PlayerModules.tick(mc);
            CorzModules.tick(mc);
            AutoArmorModule.tick(mc);
            AutoTotemModule.tick(mc);
            AntiTrapModule.tick(mc);
            TriggerBotModule.tick(mc);
            AutoToolModule.tick(mc);

            // Schematic builder: the controller owns the loaded file, the anchor and the
            // executor, and rebuilds the plan only when the hotbar or the anchor changed.
            SchematicController.tick(mc);
        });

        // Status panel.
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("dawolclient", "status"),
                (graphics, deltaTracker) -> HudRenderer.render(graphics));

        LOGGER.info("[dawol] initialised for Minecraft 26.3");
    }

    /**
     * Re-reads every chunk currently held by the client chunk cache. Used by the manual
     * re-scan key, since chunk-load events only fire for chunks arriving after the feature
     * was switched on.
     */
    private static void rescanLoadedChunks(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        int radius = Math.max(1, Math.min(Toggles.scanRadiusChunks, 24));
        int centerX = mc.player != null ? mc.player.chunkPosition().x() : 0;
        int centerZ = mc.player != null ? mc.player.chunkPosition().z() : 0;

        int scanned = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false);
                if (chunk == null) {
                    continue;
                }
                try {
                    if (Toggles.stashFinder) {
                        StashFinder.scan(level, chunk);
                    }
                    if (Toggles.baseFinder) {
                        BaseFinder.scan(level, chunk);
                    }
                    if (Toggles.chunkEsp) {
                        ChunkTracker.scan(level, chunk);
                    }
                    BlockEspRenderer.scanChunk(level, chunk);
                    scanned++;
                } catch (RuntimeException e) {
                    LOGGER.warn("[dawol] rescan failed at chunk {},{}: {}",
                            centerX + dx, centerZ + dz, e.toString());
                }
            }
        }

        LOGGER.info("[dawol] rescan complete: {} chunks, {}", scanned, StashFinder.summary());
    }
}