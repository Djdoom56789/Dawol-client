package com.dawol.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * HoleESP — highlights 1×1 holes suitable for crystal PvP.
 *
 * A "hole" is a column where:
 *  - The air space has at least 2 blocks of head-room
 *  - The floor is solid
 *  - All four horizontal neighbours of the floor block are solid or bedrock
 *
 * Bedrock-bounded holes are shown in a distinct color because they are
 * indestructible — the player can only be forced out, not dug out.
 *
 * The scan runs on the render thread inside BEFORE_GIZMOS (cheap — it only
 * looks in a small radius). Register in DawolClient.java:
 *
 *   LevelRenderEvents.BEFORE_GIZMOS.register(ctx ->
 *       HoleESP.render(ctx, tracerAnchor));
 */
public final class HoleESP {

    private HoleESP() {}

    private static final int MAX_RADIUS = 12; // blocks from player feet

    /** Called from BEFORE_GIZMOS. */
    public static void render(LevelRenderContext ctx, Vec3 anchor) {
        Toggles t = DawolClient.toggles();
        if (!t.holeEspEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Level level = mc.level;
        BlockPos origin = mc.player.blockPosition();
        int r = Math.min(MAX_RADIUS, t.holeEspRadius);

        List<HoleEntry> holes = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    int fx = origin.getX() + dx;
                    int fy = origin.getY() + dy;
                    int fz = origin.getZ() + dz;

                    // floor must be solid
                    p.set(fx, fy, fz);
                    if (!level.getBlockState(p).isSolidRender()) continue;

                    // two air blocks above the floor
                    p.set(fx, fy + 1, fz);
                    if (!level.getBlockState(p).isAir()) continue;
                    p.set(fx, fy + 2, fz);
                    if (!level.getBlockState(p).isAir()) continue;

                    // all four neighbours of the foot-block must be solid
                    boolean nX = isSolid(level, fx + 1, fy + 1, fz);
                    boolean pX = isSolid(level, fx - 1, fy + 1, fz);
                    boolean nZ = isSolid(level, fx, fy + 1, fz + 1);
                    boolean pZ = isSolid(level, fx, fy + 1, fz - 1);
                    if (!(nX && pX && nZ && pZ)) continue;

                    // bedrock check: all four AND the floor are bedrock?
                    boolean allBedrock = isBlock(level, fx, fy, fz, Blocks.BEDROCK)
                            && isBlock(level, fx + 1, fy + 1, fz, Blocks.BEDROCK)
                            && isBlock(level, fx - 1, fy + 1, fz, Blocks.BEDROCK)
                            && isBlock(level, fx, fy + 1, fz + 1, Blocks.BEDROCK)
                            && isBlock(level, fx, fy + 1, fz - 1, Blocks.BEDROCK);

                    holes.add(new HoleEntry(fx, fy + 1, fz, allBedrock));
                    break; // found hole at this x,z column, stop scanning y
                }
            }
        }

        for (HoleEntry h : holes) {
            // flat quad at the foot level
            AABB box = new AABB(h.x, h.y, h.z, h.x + 1.0, h.y + 0.02, h.z + 1.0)
                    .deflate(0.01, 0, 0.01);
            int color = h.bedrock ? 0xFF00CFFF : 0xFF5DE07A;
            GizmoStyle style = GizmoStyle.alwaysOnTopStroke(color, 1.5f);
            Gizmos.box(box, style);

            // vertical indicator line from floor to head height
            if (t.holeEspTracers) {
                Vec3 bottom = new Vec3(h.x + 0.5, h.y, h.z + 0.5);
                Vec3 top    = new Vec3(h.x + 0.5, h.y + 2.0, h.z + 0.5);
                Gizmos.line(bottom, top, GizmoStyle.alwaysOnTopStroke(color & 0x55FFFFFF, 1.0f));
            }
        }
    }

    private static boolean isSolid(Level level, int x, int y, int z) {
        BlockState s = level.getBlockState(new BlockPos(x, y, z));
        return s.isSolidRender() || s.is(Blocks.BEDROCK);
    }

    private static boolean isBlock(Level level, int x, int y, int z,
                                    net.minecraft.world.level.block.Block block) {
        return level.getBlockState(new BlockPos(x, y, z)).is(block);
    }

    private record HoleEntry(int x, int y, int z, boolean bedrock) {}
}
