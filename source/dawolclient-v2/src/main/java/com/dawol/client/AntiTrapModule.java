package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * AntiTrapModule — breaks nearby armor stands that block movement.
 *
 * Armor stand traps work by placing many armor stands in the player's
 * bounding box so collision pushes the player into walls. This module
 * attacks the nearest armor stand within reach once per delay window,
 * using the vanilla left-click attack path (no packet injection).
 *
 * The attack goes through Minecraft.gameMode.attack(), the same code
 * path as clicking on a mob. No reach extension — only stands within
 * vanilla reach (4.5 blocks for survival).
 *
 * This implements the hollow Toggles.antiTrap flag in CorzModules.
 * Call from ClientTickEvents.END_CLIENT_TICK:
 *   AntiTrapModule.tick(mc);
 */
public final class AntiTrapModule {

    private AntiTrapModule() {}

    private static long nextActionMs = 0;
    private static final double REACH_SQ = 4.5 * 4.5;

    public static void tick(Minecraft mc) {
        Toggles t = DawolClient.toggles();
        if (!t.antiTrapEnabled) return;
        if (mc.player == null || mc.level == null) return;
        if (!DawolClient.isAllowed(mc)) return;

        long now = System.currentTimeMillis();
        if (now < nextActionMs) return;

        Player player = mc.player;
        AABB searchBox = player.getBoundingBox().inflate(5.0);

        List<ArmorStand> stands = mc.level.getEntitiesOfClass(
                ArmorStand.class, searchBox, e -> !e.isInvisible());

        if (stands.isEmpty()) return;

        // Attack the nearest one within reach
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
                t.antiTrapDelayMinMs, t.antiTrapDelayMaxMs);
    }
}
