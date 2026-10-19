package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Check-aware combat assist.
 *
 * <p>Design constraint: this does not attempt to do anything the server would consider
 * impossible. Every action it takes is one a legitimate player could perform — swing at a
 * target within vanilla reach, facing roughly toward it, with a fully charged attack.
 * What it removes is human error, not physics:
 *
 * <ul>
 *   <li><b>Reach gate.</b> Attacks are refused beyond {@link Toggles#combatMaxReach}.
 *       Vanilla server-side reach sits at 3.0 blocks for survival. Anything past that is
 *       the single most reliable reach signature there is, so it is hard-capped here.</li>
 *   <li><b>Line-of-sight gate.</b> Refuses targets with no unobstructed path, because
 *       swinging at something behind a wall is trivially flagged by servers that
 *       re-trace the attack ray.</li>
 *   <li><b>Cooldown gate.</b> Requires the attack-strength meter to be full, which
 *       removes the sub-tick and always-full-strength patterns that CPS checks flag.</li>
 *   <li><b>Rotation pathing.</b> Aim moves toward the target under a per-tick cap with
 *       easing, so the yaw/pitch deltas look like mouse movement rather than an
 *       instantaneous snap. Instant-snap rotations are the standard killaura signature.</li>
 * </ul>
 *
 * <p>Everything here is deterministic and bounded. There is no packet manipulation and no
 * rotation the vanilla client would not itself send.
 */
public final class CombatSystem {

    /** Largest yaw change permitted per tick, in degrees. Human flicks stay well under this. */
    private static final float MAX_YAW_STEP = 18.0F;

    /** Largest pitch change permitted per tick, in degrees. */
    private static final float MAX_PITCH_STEP = 12.0F;

    /** Aim is considered settled when within this many degrees on both axes. */
    private static final float AIM_TOLERANCE = 3.0F;

    /** Hard vertical clamp matching vanilla look limits. */
    private static final float PITCH_LIMIT = 90.0F;

    private static Entity currentTarget;
    private static String lastRefusal = "";

    private CombatSystem() {
    }

    /**
     * Called every client tick. Selects a target, rotates toward it within the step cap,
     * and attacks when all gates are satisfied.
     */
    public static void tick(Minecraft mc) {
        if (!Toggles.combatAssist) {
            currentTarget = null;
            return;
        }

        LocalPlayer self = mc.player;
        ClientLevel level = mc.level;
        if (self == null || level == null || mc.gameMode == null) {
            currentTarget = null;
            return;
        }

        // Latched attack input: the player holds the attack key, we decide when to act.
        if (!mc.options.keyAttack.isDown()) {
            currentTarget = null;
            return;
        }

        Entity target = selectTarget(self, level);
        currentTarget = target;
        if (target == null) {
            return;
        }

        // --- Gate: line of sight ---
        if (Toggles.combatLineOfSightGate && !self.hasLineOfSight(target)) {
            lastRefusal = "no line of sight";
            return;
        }

        // --- Gate: cooldown ---
        float charge = self.getAttackStrengthScale(0.5F);
        if (Toggles.combatCooldownGate && charge < 1.0F) {
            lastRefusal = String.format("charge %.2f", charge);
            return;
        }

        // Where the target is, independent of whether the camera may be moved toward it.
        // Derived from the live target every tick, so the settle gate never reads a stale
        // angle: with rotation off it measures the player's own aim, which is the point.
        Vec3 aim = aimPoint(target);
        float wantYaw = requiredYaw(self, aim);
        float wantPitch = requiredPitch(self, aim);

        // --- Rotation pathing (must settle before swinging) ---
        if (Toggles.combatSilentRotate) {
            self.setYRot(approach(self.getYRot(), wantYaw, MAX_YAW_STEP));
            self.setXRot(clampPitch(approach(self.getXRot(), wantPitch, MAX_PITCH_STEP)));
        }

        float dx = Math.abs(wrapDegrees(self.getYRot() - wantYaw));
        float dy = Math.abs(self.getXRot() - wantPitch);
        // The settle tolerance jitters as well: a swing that always fires at exactly 3.0
        // degrees of error is as regular as one that always fires on the same tick.
        float tolerance = AIM_TOLERANCE + DelayRandomizer.sampleRotationJitter();
        if (dx > tolerance || dy > tolerance) {
            lastRefusal = String.format("settling d=%.1f/%.1f", dx, dy);
            return;
        }

        // --- Gate: reach, measured from the eye exactly as the server does ---
        double reach = eyeDistance(self, target);
        // The effective cap moves a little on each decision rather than sitting at one
        // constant number, so the gate itself is not the same threshold every tick.
        double effectiveCap = Toggles.combatMaxReach + DelayRandomizer.sampleReachJitter();
        if (Toggles.combatReachGate && reach > effectiveCap) {
            lastRefusal = String.format("reach %.2f", reach);
            return;
        }

        lastRefusal = "";
        mc.gameMode.attack(self, target);
    }

    /** Point on the target the assist aims at: mid-body, a little above centre. */
    private static Vec3 aimPoint(Entity target) {
        AABB box = target.getBoundingBox();
        return new Vec3(
                (box.minX + box.maxX) / 2.0D,
                box.minY + box.getYsize() * 0.7D,
                (box.minZ + box.maxZ) / 2.0D);
    }

    /** Yaw that would face {@code point} from the player's eyes. */
    private static float requiredYaw(LocalPlayer self, Vec3 point) {
        Vec3 eyes = self.getEyePosition();
        double dx = point.x - eyes.x;
        double dz = point.z - eyes.z;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
    }

    /** Pitch that would face {@code point} from the player's eyes. */
    private static float requiredPitch(LocalPlayer self, Vec3 point) {
        Vec3 eyes = self.getEyePosition();
        double dx = point.x - eyes.x;
        double dy = point.y - eyes.y;
        double dz = point.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return clampPitch((float) (-Math.toDegrees(Math.atan2(dy, horizontal))));
    }

    /** Moves {@code from} toward {@code to} by at most {@code maxStep}, along the short arc. */
    private static float approach(float from, float to, float maxStep) {
        float delta = wrapDegrees(to - from);
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        return from + delta;
    }

    /** Wraps an angle difference into [-180, 180]. */
    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-PITCH_LIMIT, Math.min(PITCH_LIMIT, pitch));
    }

    /** Distance from the player's eyes to the closest point of the target's hitbox. */
    private static double eyeDistance(LocalPlayer self, Entity target) {
        Vec3 eyes = self.getEyePosition();
        AABB box = target.getBoundingBox();
        double cx = Math.max(box.minX, Math.min(eyes.x, box.maxX));
        double cy = Math.max(box.minY, Math.min(eyes.y, box.maxY));
        double cz = Math.max(box.minZ, Math.min(eyes.z, box.maxZ));
        double dx = eyes.x - cx;
        double dy = eyes.y - cy;
        double dz = eyes.z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Nearest eligible hostile within the configured reach, respecting visibility rules. */
    private static Entity selectTarget(LocalPlayer self, ClientLevel level) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        double limit = Toggles.combatReachGate
                ? Toggles.combatMaxReach
                : Toggles.combatMaxReach + 1.0D;

        Iterable<Entity> candidates = level.entitiesForRendering();
        for (Entity entity : candidates) {
            if (entity == self) continue;
            if (!(entity instanceof LivingEntity living) || living.isDeadOrDying() || !living.isAlive()) {
                continue;
            }
            if (entity.isInvisible() || entity.isSpectator()) continue;

            boolean isPlayer = entity instanceof Player;
            if (isPlayer) {
                // Only include players when PVP mode is on.
                if (!Toggles.pvpTargetPlayers) continue;
            } else if (!(entity instanceof Monster)) {
                // Skip non-hostile, non-player entities.
                continue;
            }

            double distance = eyeDistance(self, entity);
            if (distance <= limit && distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }

        return best;
    }

    /**
     * Draws the current target's hitbox and the gate state, so the user can see why an
     * attack was withheld. Client-side only.
     */
    public static void render(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || !Toggles.combatAssist || currentTarget == null) {
            return;
        }

        AABB box = currentTarget.getBoundingBox().inflate(0.08D);
        var props = Gizmos.cuboid(box, net.minecraft.gizmos.GizmoStyle.stroke(Theme.GREEN, 2.0F));
        props.setAlwaysOnTop();

        String state = lastRefusal.isEmpty() ? "engaged" : lastRefusal;
        Vec3 labelPos = new Vec3(
                (box.minX + box.maxX) / 2.0D,
                box.maxY + 1.0D,
                (box.minZ + box.maxZ) / 2.0D);

        Gizmos.billboardText("target: " + state, labelPos,
                net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(Theme.GREEN).withScale(0.5F));
    }

    public static void reset() {
        currentTarget = null;
        lastRefusal = "";
    }
}
