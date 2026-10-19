package com.dawol.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StorageESP {

    private StorageESP() {}

    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final int MAX_BOXES   = 512;
    private static final int MAX_TRACERS = 32;

    public static void scanChunk(int cx, int cz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        removeChunk(cx, cz);

        LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
        if (chunk == null) return;

        List<Entry> found = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            Category cat = classify(be, mc);
            if (cat == null) continue;
            found.add(new Entry(be.getBlockPos().getX() + 0.5,
                                be.getBlockPos().getY() + 0.5,
                                be.getBlockPos().getZ() + 0.5, cat));
        }
        ENTRIES.addAll(found);
    }

    public static void removeChunk(int cx, int cz) {
        ENTRIES.removeIf(e -> (int) Math.floor(e.x) >> 4 == cx
                           && (int) Math.floor(e.z) >> 4 == cz);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static void render(LevelRenderContext ctx, Vec3 anchor) {
        if (!Toggles.storageEspEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Vec3 cam = mc.gameRenderer.mainCamera().position();

        List<Entry> snap = new ArrayList<>(ENTRIES);
        snap.sort(Comparator.comparingDouble(e -> e.distSq(cam)));
        if (snap.size() > MAX_BOXES) snap = snap.subList(0, MAX_BOXES);

        boolean throughWall = Toggles.storageEspThroughWalls;

        for (int i = 0; i < snap.size(); i++) {
            Entry e = snap.get(i);
            AABB box = new AABB(e.x - 0.5, e.y - 0.5, e.z - 0.5,
                                e.x + 0.5, e.y + 0.5, e.z + 0.5)
                    .deflate(0.02);
            int color = e.category.color;
            var props = Gizmos.cuboid(box, GizmoStyle.stroke(color, 1.5f));
            if (throughWall && props != null) props.setAlwaysOnTop();

            if (Toggles.storageEspTracers && i < MAX_TRACERS) {
                Vec3 center = new Vec3(e.x, e.y, e.z);
                var lp = Gizmos.line(anchor, center, color, 1.0f);
                if (throughWall && lp != null) lp.setAlwaysOnTop();
            }
        }
    }

    private static Category classify(BlockEntity be, Minecraft mc) {
        if (mc.level == null) return null;
        Block block = mc.level.getBlockState(be.getBlockPos()).getBlock();
        if (block == Blocks.CHEST)          return Category.CHEST;
        if (block == Blocks.TRAPPED_CHEST)  return Category.TRAPPED;
        if (block == Blocks.BARREL)         return Category.CHEST;
        if (block == Blocks.SHULKER_BOX)    return Category.SHULKER;
        if (block == Blocks.HOPPER)         return Category.HOPPER;
        if (block == Blocks.DISPENSER)      return Category.DISPENSER;
        if (block == Blocks.DROPPER)        return Category.DISPENSER;
        if (block == Blocks.FURNACE
         || block == Blocks.BLAST_FURNACE
         || block == Blocks.SMOKER)         return Category.FURNACE;
        if (block == Blocks.BREWING_STAND)  return Category.FURNACE;
        if (block == Blocks.ENDER_CHEST)    return Category.ENDER;
        return null;
    }

    enum Category {
        CHEST   (0xFF5DE07A),
        TRAPPED (0xFFFF5555),
        SHULKER (0xFFA855FF),
        HOPPER  (0xFFAAAAAA),
        DISPENSER(0xFFFF9900),
        FURNACE (0xFFFF6600),
        ENDER   (0xFF00CFFF);

        final int color;
        Category(int color) { this.color = color; }
    }

    private record Entry(double x, double y, double z, Category category) {
        double distSq(Vec3 from) {
            double dx = x - from.x, dy = y - from.y, dz = z - from.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
