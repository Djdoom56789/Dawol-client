package com.dawol.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders base-finder hits as magenta outlines with optional tracers.
 */
public final class BaseFinderRenderer {

    private BaseFinderRenderer() {
    }

    public static void render(Minecraft mc, boolean throughWalls, Vec3 tracerOrigin) {
        if (!Toggles.baseFinder) return;
        LocalPlayer self = mc.player;
        if (self == null) return;

        List<BaseFinder.Hit> hits = BaseFinder.bases();
        float rangeSquared = Toggles.baseRange * Toggles.baseRange;
        Vec3 eye = self.getEyePosition();

        for (BaseFinder.Hit hit : hits) {
            Vec3 center = Vec3.atCenterOf(hit.center());
            if (eye.distanceToSqr(center) > rangeSquared) continue;

            AABB box = new AABB(
                    hit.center().getX(), hit.center().getY() - 1, hit.center().getZ(),
                    hit.center().getX() + 16, hit.center().getY() + 4, hit.center().getZ() + 16);

            var props = Gizmos.cuboid(box, GizmoStyle.stroke(Theme.BASE, 1.5f));
            if (throughWalls) props.setAlwaysOnTop();

            if (tracerOrigin != null && Toggles.espTracers) {
                var line = Gizmos.line(tracerOrigin, center, Theme.BASE, 1.0f);
                if (throughWalls) line.setAlwaysOnTop();
            }

            String label = String.format("base %.0f", hit.score());
            Gizmos.billboardText(label, center.add(0, 5, 0),
                    TextGizmo.Style.forColorAndCentered(Theme.BASE).withScale(0.4f));
        }
    }
}
