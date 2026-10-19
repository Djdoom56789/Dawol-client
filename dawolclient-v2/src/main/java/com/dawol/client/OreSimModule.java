package com.dawol.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OreSimModule — highlights ancient debris (netherite ore) in two ways:
 *
 *  1. SCAN: reads blocks in loaded Nether chunks — reliable, works on any
 *     server where the client receives block data.
 *
 *  2. PREDICT (single-player only): simulates ancient debris placement from
 *     the world seed and shows predicted positions in un-scanned chunks.
 *     Ancient debris generates as:
 *       - 2 small blobs (count 1–3) at random Y in [8, 119]
 *       - 1 targeted blob at Y ~15 (±4)
 *     per chunk, using a Random seeded with the chunk's large-feature seed.
 *
 * Both paths respect Toggles.oreSimMode (SCAN / PREDICT / BOTH).
 *
 * Register in DawolClient.java:
 *   LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> OreSimModule.render(ctx, tracerAnchor));
 *   ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
 *       OreSimModule.scanChunk(chunk.getPos().x, chunk.getPos().z));
 *   ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
 *       OreSimModule.removeChunk(chunk.getPos().x, chunk.getPos().z));
 */
public final class OreSimModule {

    private OreSimModule() {}

    private static final CopyOnWriteArrayList<OreEntry> SCAN_ENTRIES    = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<OreEntry> PREDICT_ENTRIES = new CopyOnWriteArrayList<>();

    private static final int MAX_MARKERS = 256;

    // -----------------------------------------------------------------------
    // Chunk events

    public static void scanChunk(int cx, int cz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level.dimension() != Level.NETHER) return; // ancient debris is Nether-only

        removeChunk(cx, cz);

        Toggles t = DawolClient.toggles();
        Level level = mc.level;
        List<OreEntry> found = new ArrayList<>();

        if (t.oreSimScan) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int y = 8; y <= 119; y++) {
                        BlockPos p = new BlockPos(cx * 16 + lx, y, cz * 16 + lz);
                        if (level.getBlockState(p).is(Blocks.ANCIENT_DEBRIS)) {
                            found.add(new OreEntry(p, false));
                        }
                    }
                }
            }
            SCAN_ENTRIES.addAll(found);
        }

        if (t.oreSimPredict) {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
                long worldSeed = server.overworld().getSeed(); // overworld seed
                // Nether seed = worldSeed XOR Nether dimension seed constant (0)
                List<OreEntry> predicted = predictChunk(cx, cz, worldSeed);
                PREDICT_ENTRIES.addAll(predicted);
            }
        }
    }

    public static void removeChunk(int cx, int cz) {
        SCAN_ENTRIES.removeIf(e    -> e.pos.getX() >> 4 == cx && e.pos.getZ() >> 4 == cz);
        PREDICT_ENTRIES.removeIf(e -> e.pos.getX() >> 4 == cx && e.pos.getZ() >> 4 == cz);
    }

    public static void clear() {
        SCAN_ENTRIES.clear();
        PREDICT_ENTRIES.clear();
    }

    // -----------------------------------------------------------------------
    // Prediction — vanilla ancient debris generation rules (26.3 / 1.21+)

    private static List<OreEntry> predictChunk(int cx, int cz, long seed) {
        List<OreEntry> result = new ArrayList<>();
        // Large-feature seed matches MC WorldgenRandom.setLargeFeatureSeed
        Random rng = largeFeatureRandom(seed, cx, cz);

        int worldX = cx << 4;
        int worldZ = cz << 4;

        // 2 attempts of "small" (count 1–3) at Y in [8, 119]
        for (int attempt = 0; attempt < 2; attempt++) {
            int y = rng.nextInt(112) + 8; // 8..119
            int lx = rng.nextInt(16);
            int lz = rng.nextInt(16);
            int count = 1 + rng.nextInt(3); // 1–3 blocks in blob
            for (int i = 0; i < count; i++) {
                int dx = rng.nextInt(3) - 1;
                int dy = rng.nextInt(3) - 1;
                int dz = rng.nextInt(3) - 1;
                result.add(new OreEntry(new BlockPos(worldX + lx + dx, y + dy, worldZ + lz + dz), true));
            }
        }

        // 1 attempt "deep" (count 1–2) at Y in [8, 24]
        {
            int y  = rng.nextInt(17) + 8; // 8..24
            int lx = rng.nextInt(16);
            int lz = rng.nextInt(16);
            int count = 1 + rng.nextInt(2);
            for (int i = 0; i < count; i++) {
                int dx = rng.nextInt(3) - 1;
                int dy = rng.nextInt(3) - 1;
                int dz = rng.nextInt(3) - 1;
                result.add(new OreEntry(new BlockPos(worldX + lx + dx, y + dy, worldZ + lz + dz), true));
            }
        }

        return result;
    }

    private static Random largeFeatureRandom(long worldSeed, int cx, int cz) {
        // matches WorldgenRandom.setLargeFeatureSeed exactly
        long seed = worldSeed
                ^ ((long) cx * 341873128712L)
                ^ ((long) cz * 132897987541L);
        return new Random(seed);
    }

    // -----------------------------------------------------------------------
    // Render

    public static void render(LevelRenderContext ctx, Vec3 anchor) {
        Toggles t = DawolClient.toggles();
        if (!t.oreSimEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.level.dimension() != Level.NETHER) return;

        int drawn = 0;

        if (t.oreSimScan) {
            for (OreEntry e : SCAN_ENTRIES) {
                if (drawn++ > MAX_MARKERS) break;
                AABB box = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(e.pos)).deflate(0.02);
                Gizmos.box(box, GizmoStyle.alwaysOnTopStroke(0xFF5DE07A, 2.0f));
                if (t.oreSimTracers) {
                    Gizmos.line(anchor, Vec3.atCenterOf(e.pos),
                            GizmoStyle.alwaysOnTopStroke(0x885DE07A, 1.0f));
                }
            }
        }

        if (t.oreSimPredict) {
            for (OreEntry e : PREDICT_ENTRIES) {
                if (drawn++ > MAX_MARKERS) break;
                AABB box = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(e.pos)).deflate(0.04);
                // predicted = dashed / dimmer color to distinguish from confirmed
                Gizmos.box(box, GizmoStyle.alwaysOnTopStroke(0xAAFF9900, 1.5f));
                if (t.oreSimTracers) {
                    Gizmos.line(anchor, Vec3.atCenterOf(e.pos),
                            GizmoStyle.alwaysOnTopStroke(0x44FF9900, 1.0f));
                }
            }
        }
    }

    private record OreEntry(BlockPos pos, boolean predicted) {}
}
