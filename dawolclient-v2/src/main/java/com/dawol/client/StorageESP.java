package com.dawol.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * StorageESP — renders boxes and tracers on nearby storage containers:
 * chests, trapped chests, barrels, shulker boxes, hoppers, dispensers,
 * droppers, furnaces, blast furnaces, smokers, brewing stands, and
 * end chests. End chests are highlighted differently (they are "public"
 * storage everyone shares).
 *
 * All rendering happens in BEFORE_GIZMOS; the entity list is rebuilt on
 * chunk load/unload. Register both hooks in DawolClient.java:
 *
 *   LevelRenderEvents.BEFORE_GIZMOS.register(ctx ->
 *       StorageESP.render(ctx, tracerAnchor));
 *   ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
 *       StorageESP.scanChunk(chunk.getPos().x, chunk.getPos().z));
 *   ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
 *       StorageESP.removeChunk(chunk.getPos().x, chunk.getPos().z));
 */
public final class StorageESP {

    private StorageESP() {}

    // all storage containers we track, updated on chunk events
    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private static final int MAX_BOXES    = 512;
    private static final int MAX_TRACERS  = 32;

    /** Called on CHUNK_LOAD. */
    public static void scanChunk(int cx, int cz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        removeChunk(cx, cz);

        List<Entry> found = new ArrayList<>();
        mc.level.blockEntityList().forEach(be -> {
            if (be.getBlockPos().getX() >> 4 != cx) return;
            if (be.getBlockPos().getZ() >> 4 != cz) return;
            Category cat = classify(be);
            if (cat == null) return;
            found.add(new Entry(be.getBlockPos().getX() + 0.5,
                                be.getBlockPos().getY() + 0.5,
                                be.getBlockPos().getZ() + 0.5, cat));
        });
        ENTRIES.addAll(found);
    }

    /** Called on CHUNK_UNLOAD. */
    public static void removeChunk(int cx, int cz) {
        ENTRIES.removeIf(e -> (int) Math.floor(e.x) >> 4 == cx
                           && (int) Math.floor(e.z) >> 4 == cz);
    }

    /** Called on level change — drop all cached entries. */
    public static void clear() {
        ENTRIES.clear();
    }

    /** Called from BEFORE_GIZMOS. */
    public static void render(LevelRenderContext ctx, Vec3 anchor) {
        Toggles t = DawolClient.toggles();
        if (!t.storageEspEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        // snapshot, sort by distance, cap
        List<Entry> snap = new ArrayList<>(ENTRIES);
        snap.sort(Comparator.comparingDouble(e -> e.distSq(cam)));
        if (snap.size() > MAX_BOXES) snap = snap.subList(0, MAX_BOXES);

        boolean throughWall = t.storageEspThroughWalls;

        for (int i = 0; i < snap.size(); i++) {
            Entry e = snap.get(i);
            AABB box = new AABB(e.x - 0.5, e.y - 0.5, e.z - 0.5,
                                e.x + 0.5, e.y + 0.5, e.z + 0.5)
                    .deflate(0.02);
            int color = e.category.color;
            GizmoStyle style = throughWall
                    ? GizmoStyle.alwaysOnTopStroke(color, 1.5f)
                    : GizmoStyle.stroke(color, 1.5f);
            Gizmos.box(box, style);

            if (t.storageEspTracers && i < MAX_TRACERS) {
                Vec3 center = new Vec3(e.x, e.y, e.z);
                Gizmos.line(anchor, center, style);
            }
        }
    }

    // -----------------------------------------------------------------------

    private static Category classify(BlockEntity be) {
        BlockEntityType<?> type = be.getType();
        if (type == BlockEntityType.CHEST)          return Category.CHEST;
        if (type == BlockEntityType.TRAPPED_CHEST)  return Category.TRAPPED;
        if (type == BlockEntityType.BARREL)         return Category.CHEST;
        if (type == BlockEntityType.SHULKER_BOX)    return Category.SHULKER;
        if (type == BlockEntityType.HOPPER)         return Category.HOPPER;
        if (type == BlockEntityType.DISPENSER)      return Category.DISPENSER;
        if (type == BlockEntityType.DROPPER)        return Category.DISPENSER;
        if (type == BlockEntityType.FURNACE
         || type == BlockEntityType.BLAST_FURNACE
         || type == BlockEntityType.SMOKER)         return Category.FURNACE;
        if (type == BlockEntityType.BREWING_STAND)  return Category.FURNACE;
        if (type == BlockEntityType.ENDER_CHEST)    return Category.ENDER;
        return null;
    }

    enum Category {
        CHEST   (0xFF5DE07A),   // green — normal storage
        TRAPPED (0xFFFF5555),   // red   — trapped
        SHULKER (0xFFA855FF),   // purple
        HOPPER  (0xFFAAAAAA),   // gray
        DISPENSER(0xFFFF9900),  // orange
        FURNACE (0xFFFF6600),   // amber
        ENDER   (0xFF00CFFF);   // cyan — ender chest

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
