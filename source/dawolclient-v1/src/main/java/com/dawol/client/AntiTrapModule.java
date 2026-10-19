package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class AntiTrapModule {

    private AntiTrapModule() {}

    private static long nextActionMs = 0;
    private static final double REACH_SQ = 4.5 * 4.5;

    public static void tick(Minecraft mc) {
        if (!Toggles.antiTrap) return;
        if (mc.player == null || mc.level == null) return;
        if (!mc.mouseHandler.isMouseGrabbed()) return;

        long now = System.currentTimeMillis();
        if (now < nextActionMs) return;

        Player player = mc.player;
        AABB searchBox = player.getBoundingBox().inflate(5.0);

        List<ArmorStand> stands = mc.level.getEntitiesOfClass(
                ArmorStand.class, searchBox, e -> !e.isInvisible());

        if (stands.isEmpty()) return;

        ArmorStand target = null;
        double bestDist = REACH_SQ;
        for (ArmorStand stand : stands) {
            double dist = stand.distanceToSqr(player.getX(), player.getY(), player.getZ());
            if (dist < bestDist) {
                bestDist = dist;
                target   = stand;
            }
        }
        if (target == null) return;

        mc.gameMode.attack(player, target);
        nextActionMs = now + DelayRandomizer.sample(
                Toggles.antiTrapDelayMinMs, Toggles.antiTrapDelayMaxMs);
    }
}
