package com.dawol.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws entity boxes, tracers, health bars and labels.
 *
 * <p>All geometry is submitted as vanilla gizmos, which the engine's own gizmo renderer
 * resolves. {@code setAlwaysOnTop()} is what makes geometry draw over terrain, so an
 * occluded player remains visible.
 *
 * <p>Every line here is client-side. Rendering produces no packets.
 */
public final class EspRenderer {

    /** Length of one health-bar strip along Y. The stacked strips keep their original spacing. */
    private static final double BAR_HEIGHT = 0.02D;
    /** Half-depth of a strip: the depth is what gives the bar volume instead of zero area. */
    private static final double BAR_DEPTH = 0.02D;

    // Cached styles. Every one of them is an immutable record, but they were still being
    // rebuilt once per entity per frame; one set per entity colour covers every call site.
    private static final StyleSet STYLE_PLAYER = styleSet(Theme.PLAYER);
    private static final StyleSet STYLE_MOB = styleSet(Theme.MOB);
    private static final StyleSet STYLE_ITEM = styleSet(Theme.ITEM);

    private static final int PLAYER_COLOR = Theme.PLAYER;
    private static final int MOB_COLOR = Theme.MOB;
    private static final int ITEM_COLOR = Theme.ITEM;
    private static final int TRACER_COLOR = Theme.TRACER;

    /** The box outline, the through-walls box fill and the label style of one entity colour. */
    private record StyleSet(GizmoStyle fill, GizmoStyle stroke, TextGizmo.Style label) {
    }

    /** A tracer line held back until every candidate has been seen, so the cap can be fair. */
    private record Tracer(Entity entity, double distanceSq, int color) {
    }

    private EspRenderer() {
    }

    /**
     * @param tracerOrigin the shared tracer origin for this frame, computed once by the
     *                     caller. Every tracer in the client takes it from there, so they all
     *                     converge on one point on screen.
     */
    public static void render(Minecraft mc, Vec3 tracerOrigin) {
        LocalPlayer self = mc.player;
        ClientLevel level = mc.level;
        if (self == null || level == null) {
            return;
        }

        Vec3 eye = self.getEyePosition();
        Vec3 selfPos = self.position();

        double range = Toggles.espRange;
        double rangeSq = range * range;

        // Tracers are their own feature: a player may want lines to targets without boxes,
        // so each entity type feeds the loop when either its box or tracers are enabled.
        boolean wantTracers = Toggles.espTracers;

        // Nearest-first so that when the tracer cap bites, the lines kept are the useful ones.
        // The cap can only bite when the level holds more entities than the budget allows,
        // which is cheap to test up front; the uncapped walk then draws each line inline.
        int tracerBudget = wantTracers ? Toggles.maxTracers : Integer.MAX_VALUE;
        int tracersDrawn = 0;
        boolean capped = wantTracers
                && level.getEntityCount() + level.players().size() > tracerBudget;
        List<Tracer> candidates = capped ? new ArrayList<>() : null;

        if (Toggles.espPlayers || wantTracers) {
            List<AbstractClientPlayer> players = level.players();
            for (AbstractClientPlayer other : players) {
                if (other == self) {
                    continue;
                }
                double distanceSq = other.position().distanceToSqr(selfPos);
                if (distanceSq > rangeSq) {
                    continue;
                }
                if (capped) {
                    // Queued rather than drawn: the cap is applied once every candidate has
                    // been seen, so the lines that survive are the closest ones.
                    candidates.add(new Tracer(other, distanceSq, PLAYER_COLOR));
                }
                boolean drawLine = wantTracers && !capped && tracersDrawn < tracerBudget;
                if (drawLine) {
                    tracersDrawn++;
                }
                if (Toggles.espPlayers || drawLine) {
                    drawEntity(eye, tracerOrigin, other, PLAYER_COLOR, Toggles.espPlayers, drawLine);
                }
            }
        }

        if (Toggles.espMobs || Toggles.espItems || wantTracers) {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity == self || entity instanceof Player) {
                    continue;
                }

                boolean isMob = entity instanceof Monster;
                boolean isItem = entity instanceof ItemEntity;
                if (!isMob && !isItem) {
                    continue;
                }

                boolean drawBox = isMob ? Toggles.espMobs : Toggles.espItems;
                if (!drawBox && !wantTracers) {
                    continue;
                }

                double distanceSq = entity.position().distanceToSqr(selfPos);
                if (distanceSq > rangeSq) {
                    continue;
                }

                int color = isMob ? MOB_COLOR : ITEM_COLOR;
                if (capped) {
                    candidates.add(new Tracer(entity, distanceSq, color));
                }
                boolean drawLine = wantTracers && !capped && tracersDrawn < tracerBudget;
                if (drawLine) {
                    tracersDrawn++;
                }
                if (drawBox || drawLine) {
                    drawEntity(eye, tracerOrigin, entity, color, drawBox, drawLine);
                }
            }
        }

        if (candidates != null && !candidates.isEmpty()) {
            int drawCount = Math.min(tracerBudget, candidates.size());
            if (drawCount < candidates.size()) {
                candidates.sort(Comparator.comparingDouble(Tracer::distanceSq));
            }
            for (int i = 0; i < drawCount; i++) {
                Tracer tracer = candidates.get(i);
                drawTracer(tracerOrigin, tracer.entity(), tracer.color());
            }
        }
    }

    private static void drawEntity(Vec3 eye, Vec3 tracerOrigin, Entity entity, int color,
                                   boolean withBox, boolean withTracer) {
        AABB box = entity.getBoundingBox();
        if (box == null) {
            return;
        }

        boolean onTop = Toggles.espThroughWalls;
        // Immutable and cached, so the outline and the through-walls fill both come from
        // constants rather than being allocated per entity per frame.
        StyleSet styles = stylesFor(color);

        if (withBox) {
            applyThroughWall(
                    Gizmos.cuboid(box.inflate(0.04D), onTop ? styles.fill() : styles.stroke()), onTop);
            drawCornerAccents(box, color, onTop);
        }

        if (withTracer) {
            drawTracer(tracerOrigin, entity, color);
        }

        if (withBox && Toggles.espHealthBar && entity instanceof LivingEntity living) {
            drawHealthBar(living, box, onTop);
        }

        if (withBox && Toggles.espLabels) {
            drawLabel(entity, box, eye, styles.label());
        }
    }

    /** The line from the shared tracer origin to the entity's centre. */
    private static void drawTracer(Vec3 tracerOrigin, Entity entity, int color) {
        AABB box = entity.getBoundingBox();
        if (box == null) {
            return;
        }
        applyThroughWall(Gizmos.line(tracerOrigin, box.getCenter(), color, 1.0F), Toggles.tracersOnTop);
    }

    /**
     * Short tick marks at the four vertical edges, which read better than a bare box at range.
     *
     * <p>Written with plain locals rather than a corner array: this runs per entity per frame,
     * and the array-of-arrays plus eight Vec3 allocations were pure garbage at frame rate.
     */
    private static void drawCornerAccents(AABB box, int color, boolean onTop) {
        final double pad = 0.07D;
        final double tick = Math.max(0.12D, Math.min(0.35D, (box.maxY - box.minY) * 0.18D));

        final double x0 = box.minX - pad;
        final double x1 = box.maxX + pad;
        final double z0 = box.minZ - pad;
        final double z1 = box.maxZ + pad;

        final double top = box.maxY + pad;
        final double topInner = top - tick;
        final double bottom = box.minY - pad;
        final double bottomInner = bottom + tick;

        // Four posts: (x0,z0), (x0,z1), (x1,z0), (x1,z1).
        drawPost(x0, z0, top, topInner, bottom, bottomInner, color, onTop);
        drawPost(x0, z1, top, topInner, bottom, bottomInner, color, onTop);
        drawPost(x1, z0, top, topInner, bottom, bottomInner, color, onTop);
        drawPost(x1, z1, top, topInner, bottom, bottomInner, color, onTop);
    }

    /** One vertical corner: a tick down from the top and a tick up from the bottom. */
    private static void drawPost(double x, double z, double top, double topInner,
                                 double bottom, double bottomInner, int color, boolean onTop) {
        applyThroughWall(Gizmos.line(
                new Vec3(x, top, z), new Vec3(x, topInner, z), color, 2.0F), onTop);
        applyThroughWall(Gizmos.line(
                new Vec3(x, bottom, z), new Vec3(x, bottomInner, z), color, 2.0F), onTop);
    }

    /** Two stacked horizontal strips above the entity's head: backing bar, then fill. */
    private static void drawHealthBar(LivingEntity living, AABB box, boolean onTop) {
        float health = living.getHealth();
        float max = Math.max(1.0F, living.getMaxHealth());
        float absorption = living.getAbsorptionAmount();

        double ratio = Math.max(0.0D, Math.min(1.0D, health / max));
        double width = Math.max(0.5D, box.getXsize()) + 0.2D;
        double half = width / 2.0D;
        double y = box.maxY + 0.32D;
        double cx = (box.minX + box.maxX) / 2.0D;
        double cz = (box.minZ + box.maxZ) / 2.0D;

        applyThroughWall(bar(cx - half, cx + half, y, cz, 0xFF000000), onTop);

        double filledRight = cx - half + width * ratio;
        applyThroughWall(bar(cx - half, filledRight, y + BAR_HEIGHT, cz, healthColor((float) ratio)), onTop);

        if (absorption > 0.0F) {
            double absRatio = Math.min(1.0D, absorption / max);
            applyThroughWall(bar(cx - half, cx - half + width * absRatio, y + BAR_HEIGHT * 3.0D,
                    cz, 0xFFFFD60A), onTop);
        }
    }

    /**
     * One health-bar strip, spanning the X axis at a fixed Z.
     *
     * <p>A slab rather than a flat quad: a quad whose corners share their Y and Z has no area
     * and draws nothing, and a single-plane strip also vanishes when the camera looks along
     * the axis it lies in.
     */
    private static GizmoProperties bar(double x1, double x2, double y, double cz, int color) {
        if (x2 <= x1) {
            return null;
        }
        return Gizmos.cuboid(new AABB(x1, y, cz - BAR_DEPTH, x2, y + BAR_HEIGHT, cz + BAR_DEPTH),
                GizmoStyle.fill(color));
    }

    private static int healthColor(float ratio) {
        float clamped = Math.max(0.0F, Math.min(1.0F, ratio));
        int r = (int) (255.0F * (1.0F - clamped));
        int g = (int) (255.0F * clamped);
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    private static void drawLabel(Entity entity, AABB box, Vec3 selfPos, TextGizmo.Style style) {
        double distance = Math.sqrt(entity.position().distanceToSqr(selfPos));

        // Built by hand rather than with String.format: this runs per entity per frame, and
        // every format call allocates a Formatter plus a varargs array.
        StringBuilder text = new StringBuilder(entity.getName().getString());
        if (entity instanceof LivingEntity living) {
            text.append("  ").append(Math.round(living.getHealth() * 10.0D) / 10.0D).append('\u2764');
        }
        text.append("  ").append(Math.round(distance)).append('m');

        Vec3 pos = new Vec3((box.minX + box.maxX) / 2.0D, box.maxY + 0.75D, (box.minZ + box.maxZ) / 2.0D);
        Gizmos.billboardText(text.toString(), pos, style);
    }

    /** The styles for one entity colour: fill, outline and label. */
    private static StyleSet styleSet(int color) {
        return new StyleSet(
                GizmoStyle.strokeAndFill(color, 2.0F, (color & 0x00FFFFFF) | 0x28000000),
                GizmoStyle.stroke(color, 2.0F),
                TextGizmo.Style.forColorAndCentered(color).withScale(0.55F));
    }

    /** The cached styles matching a colour, or a fresh set for an unlisted colour. */
    private static StyleSet stylesFor(int color) {
        if (color == Theme.PLAYER) {
            return STYLE_PLAYER;
        }
        if (color == Theme.MOB) {
            return STYLE_MOB;
        }
        if (color == Theme.ITEM) {
            return STYLE_ITEM;
        }
        return styleSet(color);
    }

    private static void applyThroughWall(GizmoProperties props, boolean onTop) {
        if (onTop && props != null) {
            props.setAlwaysOnTop();
        }
    }
}

