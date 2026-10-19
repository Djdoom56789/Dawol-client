package com.dawol.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public final class OreSimModule {

    private OreSimModule() {}

    private static final CopyOnWriteArrayList<OreEntry> SCAN_ENTRIES    = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<OreEntry> PREDICT_ENTRIES = new CopyOnWriteArrayList<>();
    private static final int MAX_MARKERS = 256;

    public static void scanChunk(int cx, int cz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level.dimension() != Level.NETHER) return;

        removeChunk(cx, cz);

        List<OreEntry> found = new ArrayList<>();

        if (Toggles.oreSimScan) {
            Level level = mc.level;
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

        if (Toggles.oreSimPredict) {
            var server = mc.getSingleplayerServer();
            if (server != null) {
                long worldSeed = server.overworld().getSeed();
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

    private static List<OreEntry> predictChunk(int cx, int cz, long seed) {
        List<OreEntry> result = new ArrayList<>();
        Random rng = largeFeatureRandom(seed, cx, cz);
        int worldX = cx << 4;
        int worldZ = cz << 4;

        for (int attempt = 0; attempt < 2; attempt++) {
            int y = rng.nextInt(112) + 8;
            int lx = rng.nextInt(16);
            int lz = rng.nextInt(16);
            int count = 1 + rng.nextInt(3);
            for (int i = 0; i < count; i++) {
                int dx = rng.nextInt(3) - 1;
                int dy = rng.nextInt(3) - 1;
                int dz = rng.nextInt(3) - 1;
                result.add(new OreEntry(new BlockPos(worldX + lx + dx, y + dy, worldZ + lz + dz), true));
            }
        }

        {
            int y  = rng.nextInt(17) + 8;
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
        long seed = worldSeed
                ^ ((long) cx * 341873128712L)
                ^ ((long) cz * 132897987541L);
        return new Random(seed);
    }

    public static void render(LevelRenderContext ctx, Vec3 anchor) {
        if (!Toggles.oreSimEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.level.dimension() != Level.NETHER) return;

        int drawn = 0;

        if (Toggles.oreSimScan) {
            for (OreEntry e : SCAN_ENTRIES) {
                if (drawn++ > MAX_MARKERS) break;
                AABB box = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(e.pos)).deflate(0.02);
                var props = Gizmos.cuboid(box, GizmoStyle.stroke(0xFF5DE07A, 2.0f));
                if (props != null) props.setAlwaysOnTop();
                if (Toggles.oreSimTracers) {
                    var lp = Gizmos.line(anchor, Vec3.atCenterOf(e.pos), 0x885DE07A, 1.0f);
                    if (lp != null) lp.setAlwaysOnTop();
                }
            }
        }

        if (Toggles.oreSimPredict) {
            for (OreEntry e : PREDICT_ENTRIES) {
                if (drawn++ > MAX_MARKERS) break;
                AABB box = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(e.pos)).deflate(0.04);
                var props = Gizmos.cuboid(box, GizmoStyle.stroke(0xAAFF9900, 1.5f));
                if (props != null) props.setAlwaysOnTop();
                if (Toggles.oreSimTracers) {
                    var lp = Gizmos.line(anchor, Vec3.atCenterOf(e.pos), 0x44FF9900, 1.0f);
                    if (lp != null) lp.setAlwaysOnTop();
                }
            }
        }
    }

    private record OreEntry(BlockPos pos, boolean predicted) {}
}
