package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class TriggerBotModule {

    private TriggerBotModule() {}

    private static long nextActionMs = 0;
    private static final double MAX_REACH = 3.0;

    public static void tick(Minecraft mc) {
        if (!Toggles.triggerBotEnabled) return;
        if (mc.player == null || mc.level == null) return;
        if (!mc.mouseHandler.isMouseGrabbed()) return;

        long now = System.currentTimeMillis();
        if (now < nextActionMs) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        if (!Toggles.triggerBotAttackPlayers && entity instanceof Player) return;
        if (!living.isAlive()) return;

        double distSq = mc.player.distanceToSqr(
                entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
        if (distSq > MAX_REACH * MAX_REACH) return;

        if (mc.player.getAttackStrengthScale(0.5f) < Toggles.triggerBotCooldownThreshold) return;

        mc.gameMode.attack(mc.player, entity);
        nextActionMs = now + DelayRandomizer.sample(
                Toggles.triggerBotDelayMinMs, Toggles.triggerBotDelayMaxMs);
    }
}
