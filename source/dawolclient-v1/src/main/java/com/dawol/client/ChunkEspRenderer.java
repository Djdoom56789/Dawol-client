package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws column outlines per loaded chunk:
 * cyan for new chunks, yellow for ore-dense chunks.
 */
public final class ChunkEspRenderer {

    private ChunkEspRenderer() {
    }

    public static void render(Minecraft mc, boolean throughWalls) {
        if (!Toggles.chunkEsp) return;
        if (mc.level == null || mc.player == null) return;

        int playerCX = mc.player.chunkPosition().x();
        int playerCZ = mc.player.chunkPosition().z();
        int range = (int) (Toggles.blockEspRange / 16.0F) + 1;

        double minY = mc.level.getMinY();
        double maxY = mc.level.getMaxY();

        for (int dz = -range; dz <= range; dz++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = playerCX + dx;
                int cz = playerCZ + dz;

                boolean showNew   = Toggles.chunkNewHighlight && ChunkTracker.isNew(cx, cz);
                boolean showDense = Toggles.chunkDensityHighlight && ChunkTracker.isDense(cx, cz);

                if (!showNew && !showDense) continue;

                int color = showNew ? Theme.CHUNK_NEW : Theme.CHUNK_DENSE;

                double x0 = cx * 16.0;
                double z0 = cz * 16.0;
                double x1 = x0 + 16.0;
                double z1 = z0 + 16.0;

                GizmoStyle style = GizmoStyle.stroke(color, 1.0F);

                // Corner posts
                for (int i = 0; i < 4; i++) {
                    double x = (i & 1) == 0 ? x0 : x1;
                    double z = (i & 2) == 0 ? z0 : z1;
                    var post = Gizmos.line(new Vec3(x, minY, z), new Vec3(x, maxY, z), color, 1.0F);
                    if (throughWalls && post != null) post.setAlwaysOnTop();
                }

                // Cap boxes at top and bottom
                var bottom = Gizmos.cuboid(new AABB(x0, minY, z0, x1, minY + 4.0, z1), style);
                var top    = Gizmos.cuboid(new AABB(x0, maxY - 4.0, z0, x1, maxY, z1), style);
                if (throughWalls) {
                    if (bottom != null) bottom.setAlwaysOnTop();
                    if (top    != null) top.setAlwaysOnTop();
                }
            }
        }
    }
}
