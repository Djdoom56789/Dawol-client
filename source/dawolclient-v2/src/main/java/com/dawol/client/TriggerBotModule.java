package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * TriggerBotModule — left-clicks when a live entity is under the crosshair.
 *
 * Conditions before a click fires:
 *  1. The entity hit by the crosshair ray is a LivingEntity (not a player
 *     unless t.triggerBotAttackPlayers is true).
 *  2. The entity is within vanilla survival reach (not reach-extended).
 *  3. The player's attack cooldown (getAttackStrengthScale) is ≥ 0.9.
 *  4. The delay window since the last attack has elapsed (humanised).
 *
 * The attack fires via Minecraft.gameMode.attack() — same as a mouse click.
 * No rotation packets, no aim assist, no reach extension.
 *
 * Call from ClientTickEvents.END_CLIENT_TICK:
 *   TriggerBotModule.tick(mc);
 */
public final class TriggerBotModule {

    private TriggerBotModule() {}

    private static long nextActionMs = 0;
    /** Vanilla survival reach in blocks. */
    private static final double MAX_REACH = 3.0;

    public static void tick(Minecraft mc) {
        Toggles t = DawolClient.toggles();
        if (!t.triggerBotEnabled) return;
        if (mc.player == null || mc.level == null) return;
        if (!DawolClient.isAllowed(mc)) return;

        long now = System.currentTimeMillis();
        if (now < nextActionMs) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        if (!t.triggerBotAttackPlayers && entity instanceof Player) return;
        if (!living.isAlive()) return;

        // Distance gate — use vanilla reach only
        double distSq = mc.player.distanceToSqr(
                entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
        if (distSq > MAX_REACH * MAX_REACH) return;

        // Cooldown gate — don't swing on a cold sword
        if (mc.player.getAttackStrengthScale(0.5f) < t.triggerBotCooldownThreshold) return;

        mc.gameMode.attack(mc.player, entity);
        nextActionMs = now + DelayRandomizer.sample(
                t.triggerBotDelayMinMs, t.triggerBotDelayMaxMs);
    }
}
