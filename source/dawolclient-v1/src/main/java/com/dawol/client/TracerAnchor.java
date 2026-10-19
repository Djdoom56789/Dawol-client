package com.dawol.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * The world position that corresponds to the centre of the screen.
 *
 * <p>Every tracer originates here, so lines converge on the crosshair and fan out to their
 * targets.
 *
 * <p><b>Why the offset is a full block and not a hair.</b> Any point along the view axis
 * projects to screen centre, so the distance is free to choose — and choosing it badly is
 * what broke tracers. At a few centimetres the origin lands inside the camera's near clip
 * plane, where a line's start point is unreliable and the tracer silently fails to appear.
 * Placing the origin a metre-plus ahead keeps it comfortably inside the frustum while
 * projecting to the same pixel, because it is still exactly on the view axis.
 *
 * <p>The anchor is therefore fixed in <b>screen</b> space rather than world space: it tracks
 * the camera's orientation only, never its position relative to the world.
 */
public final class TracerAnchor {

    /**
     * Distance along the view direction to place the anchor, in blocks.
     *
     * <p>Large enough to sit well clear of the near clip plane. Too close and the tracer's
     * start point is clipped away; this value is deliberately generous for that reason.
     */
    public static final double ORIGIN_DISTANCE = 1.5D;

    private TracerAnchor() {
    }

    /** The anchor for the current camera. */
    public static Vec3 of(Camera camera, net.minecraft.world.entity.Entity cameraEntity) {
        return of(camera.position(), cameraEntity.getYRot(), cameraEntity.getXRot());
    }

    /**
     * Pure form: eye position and orientation in, crosshair anchor out.
     *
     * <p>Separated so the geometry is verifiable without a camera, an entity or a world.
     */
    public static Vec3 of(Vec3 eye, float yawDegrees, float pitchDegrees) {
        double yawRad = Math.toRadians(yawDegrees);
        double pitchRad = Math.toRadians(pitchDegrees);

        // Forward vector from yaw/pitch in vanilla's convention: yaw 0 faces +Z.
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3(
                eye.x + fx * ORIGIN_DISTANCE,
                eye.y + fy * ORIGIN_DISTANCE,
                eye.z + fz * ORIGIN_DISTANCE);
    }
}