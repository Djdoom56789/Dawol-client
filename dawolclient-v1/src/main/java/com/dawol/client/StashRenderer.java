package com.dawol.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws stash-finder hits as chunk columns with a tracer to each.
 *
 * <p>Two markers per hit, both chosen for readability at range:
 * <ul>
 *   <li>a <b>column outline</b> spanning the chunk's vertical extent, so a hit reads as an
 *       area rather than a point, and is visible whether the stash is shallow or deep;</li>
 *   <li>a <b>tracer from the crosshair</b> to the actual container position, which is where
 *       you need to dig.</li>
 * </ul>
 *
 * <p>Per-frame work is bounded: hits are distance-culled, sorted nearest-first, and capped,
 * so a large number of recorded stashes cannot degrade the frame.
 */
public final class StashRenderer {

    /** Maximum columns drawn in one frame. */
    private static final int MAX_MARKERS = 48;

    /** Half-height of the marker box drawn around a container position. */
    private static final double ANCHOR_HALF = 1.0D;

    private StashRenderer() {
    }

    /**
     * @param tracerOrigin the shared tracer origin for this frame, computed once by the
     *                     caller, so stash tracers converge on the same point as every other.
     */
    public static void render(Minecraft mc, boolean showAllChunks, Vec3 tracerOrigin) {
        if (!Toggles.stashFinder) {
            return;
        }

        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) {
            return;
        }

        Vec3 eye = self.getEyePosition();

        List<StashFinder.Hit> candidates = showAllChunks ? StashFinder.all() : StashFinder.stashes();
        if (candidates.isEmpty()) {
            return;
        }

        double range = Toggles.stashRange;
        double rangeSq = range * range;

        // Cull to range and order by distance before capping, so the limited marker budget
        // goes to the nearest hits rather than whatever the score order happens to put first.
        // The list is local: the cached snapshot other callers share is left untouched.
        List<Marker> markers = new ArrayList<>();
        for (StashFinder.Hit hit : candidates) {
            BlockPosLike center = BlockPosLike.of(hit);
            double dx = center.x - eye.x;
            double dz = center.z - eye.z;
            double distSq = dx * dx + dz * dz;
            if (distSq > rangeSq) {
                continue;
            }
            markers.add(new Marker(hit, center, distSq));
        }
        markers.sort(Comparator.comparingDouble(Marker::distSq));

        int drawn = 0;
        for (Marker marker : markers) {
            if (drawn >= MAX_MARKERS) {
                break;
            }

            StashFinder.Hit hit = marker.hit();
            boolean flagged = hit.isStash();
            int color = flagged ? Theme.STASH : Theme.CHUNK_NEW;

            drawColumn(mc, hit, color);
            drawAnchor(hit, marker.center(), tracerOrigin, color, flagged, eye);
            drawn++;
        }
    }

    /** Chunk-sized column outline from build floor to ceiling. */
    private static void drawColumn(Minecraft mc, StashFinder.Hit hit, int color) {
        double x0 = hit.chunkX * 16.0D;
        double z0 = hit.chunkZ * 16.0D;
        double minY = mc.level.getMinY();
        double maxY = mc.level.getMaxY();

        GizmoStyle style = GizmoStyle.stroke(color, 1.0F);

        // Corner posts only: cheaper than a full-height box and reads just as clearly.
        for (int i = 0; i < 4; i++) {
            double x = x0 + ((i & 1) == 0 ? 0.0D : 16.0D);
            double z = z0 + ((i & 2) == 0 ? 0.0D : 16.0D);
            applyThroughWall(Gizmos.line(new Vec3(x, minY, z), new Vec3(x, maxY, z), color, 1.0F));
        }

        // A thin cap at each end so the column has a readable top and bottom.
        AABB bottom = new AABB(x0, minY, z0, x0 + 16.0D, minY + 4.0D, z0 + 16.0D);
        AABB top = new AABB(x0, maxY - 4.0D, z0, x0 + 16.0D, maxY, z0 + 16.0D);
        applyThroughWall(Gizmos.cuboid(bottom, style));
        applyThroughWall(Gizmos.cuboid(top, style));
    }

    /** Box and tracer at the container position, which is where you actually dig. */
    private static void drawAnchor(StashFinder.Hit hit, BlockPosLike center, Vec3 tracerOrigin,
                                   int color, boolean flagged, Vec3 eye) {
        Vec3 point = new Vec3(center.x, center.y, center.z);

        AABB box = new AABB(
                center.x - ANCHOR_HALF, center.y - ANCHOR_HALF, center.z - ANCHOR_HALF,
                center.x + ANCHOR_HALF, center.y + ANCHOR_HALF, center.z + ANCHOR_HALF);

        applyThroughWall(Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, 2.0F,
                (color & 0x00FFFFFF) | 0x30000000)));

        // Tracer always originates at the crosshair, like every other tracer in the client.
        applyThroughWall(Gizmos.line(tracerOrigin, point, color, 1.0F));

        double distance = Math.sqrt(eye.distanceToSqr(point));
        String label = (flagged ? "stash " : "chunk ")
                + hit.containers + "c " + String.format("%.0fm", distance);
        Gizmos.billboardText(label, new Vec3(center.x, center.y + 1.5D, center.z),
                TextGizmo.Style.forColorAndCentered(color).withScale(0.5F));
    }

    private static void applyThroughWall(GizmoProperties props) {
        if (Toggles.stashThroughWalls && props != null) {
            props.setAlwaysOnTop();
        }
    }

    /**
     * An in-range hit paired with its position carrier and squared horizontal distance,
     * so the cap can be applied to the nearest hits first.
     */
    private record Marker(StashFinder.Hit hit, BlockPosLike center, double distSq) {
    }

    /**
     * Minimal position carrier.
     *
     * <p>Exists so the renderer never constructs a {@code BlockPos} per marker per frame;
     * these are plain doubles pulled straight from the hit.
     */
    private static final class BlockPosLike {
        final double x;
        final double y;
        final double z;

        private BlockPosLike(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static BlockPosLike of(StashFinder.Hit hit) {
            var pos = hit.center();
            return new BlockPosLike(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        }
    }
}