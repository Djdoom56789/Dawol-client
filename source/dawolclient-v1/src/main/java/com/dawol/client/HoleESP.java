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

public final class HoleESP {

    private HoleESP() {}

    private static final int MAX_RADIUS = 12;

    public static void render(LevelRenderContext ctx, Vec3 anchor) {
        if (!Toggles.holeEspEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Level level = mc.level;
        BlockPos origin = mc.player.blockPosition();
        int r = Math.min(MAX_RADIUS, Toggles.holeEspRadius);

        List<HoleEntry> holes = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    int fx = origin.getX() + dx;
                    int fy = origin.getY() + dy;
                    int fz = origin.getZ() + dz;

                    p.set(fx, fy, fz);
                    if (!level.getBlockState(p).isSolidRender()) continue;

                    p.set(fx, fy + 1, fz);
                    if (!level.getBlockState(p).isAir()) continue;
                    p.set(fx, fy + 2, fz);
                    if (!level.getBlockState(p).isAir()) continue;

                    boolean nX = isSolid(level, fx + 1, fy + 1, fz);
                    boolean pX = isSolid(level, fx - 1, fy + 1, fz);
                    boolean nZ = isSolid(level, fx, fy + 1, fz + 1);
                    boolean pZ = isSolid(level, fx, fy + 1, fz - 1);
                    if (!(nX && pX && nZ && pZ)) continue;

                    boolean allBedrock = isBlock(level, fx, fy, fz, Blocks.BEDROCK)
                            && isBlock(level, fx + 1, fy + 1, fz, Blocks.BEDROCK)
                            && isBlock(level, fx - 1, fy + 1, fz, Blocks.BEDROCK)
                            && isBlock(level, fx, fy + 1, fz + 1, Blocks.BEDROCK)
                            && isBlock(level, fx, fy + 1, fz - 1, Blocks.BEDROCK);

                    holes.add(new HoleEntry(fx, fy + 1, fz, allBedrock));
                    break;
                }
            }
        }

        for (HoleEntry h : holes) {
            AABB box = new AABB(h.x, h.y, h.z, h.x + 1.0, h.y + 0.02, h.z + 1.0)
                    .deflate(0.01, 0, 0.01);
            int color = h.bedrock ? 0xFF00CFFF : 0xFF5DE07A;
            var props = Gizmos.cuboid(box, GizmoStyle.stroke(color, 1.5f));
            if (props != null) props.setAlwaysOnTop();

            if (Toggles.holeEspTracers) {
                Vec3 bottom = new Vec3(h.x + 0.5, h.y, h.z + 0.5);
                Vec3 top    = new Vec3(h.x + 0.5, h.y + 2.0, h.z + 0.5);
                var lp = Gizmos.line(bottom, top, color & 0x55FFFFFF, 1.0f);
                if (lp != null) lp.setAlwaysOnTop();
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
