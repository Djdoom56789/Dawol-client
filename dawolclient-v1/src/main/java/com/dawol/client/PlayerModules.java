package com.dawol.client;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;

/**
 * Player-category modules.
 *
 * <p>Everything here works through public client APIs. Nothing sends a packet the vanilla
 * client would not itself send, and nothing patches game code.
 */
public final class PlayerModules {

    private static int respawnCooldown = 0;

    /** Set when an idle-kick style disconnect message is seen. Surfaced by the HUD. */
    private static volatile String lastKickReason = null;

    private PlayerModules() {
    }

    /** Called once per client tick. */
    public static void tick(Minecraft mc) {
        if (respawnCooldown > 0) {
            respawnCooldown--;
        }
        autoRespawn(mc);
        autoSprint(mc);
        autoEat(mc);
    }

    /**
     * Respawns the moment the player is dead.
     *
     * <p>In 26.3 the only respawn trigger is the death screen's Respawn button, whose
     * handler calls {@code LocalPlayer.respawn()}; closing the screen alone would leave the
     * player dead with no way back, because the death screen cannot be escaped with Esc.
     * A short cooldown stops a respawn loop if the server kills the player again
     * immediately.
     */
    private static void autoRespawn(Minecraft mc) {
        if (!Toggles.autoRespawn || respawnCooldown > 0) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || !player.isDeadOrDying()) {
            return;
        }

        // Minecraft.screen is private in 26.3, so the player's own death state drives this
        // instead of inspecting the screen.
        player.respawn();
        mc.setScreenAndShow(null);
        respawnCooldown = 20;
    }

    /**
     * Records a disconnect that looks like an idle or AFK kick.
     *
     * <p>Deliberately does <b>not</b> fabricate movement. Sending input the player is not
     * making is precisely what movement checks look for, so this module's honest job is to
     * identify the kick and report it, not to hide from it.
     */
    public static void noteDisconnectReason(String reason) {
        if (reason == null) {
            return;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        if (lower.contains("idle") || lower.contains("afk") || lower.contains("kick")
                || lower.contains("timed out") || lower.contains("timeout")) {
            lastKickReason = reason;
            DawolClient.LOGGER.warn("[dawol] idle-kick style disconnect: {}", reason);
        }
    }

    private static void autoSprint(Minecraft mc) {
        if (!Toggles.autoSprint || mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;
        if (!player.isUnderWater() && !player.isSwimming()
                && !player.isSprinting()
                && player.getFoodData().getFoodLevel() > 6) {
            player.setSprinting(true);
        }
    }

    private static void autoEat(Minecraft mc) {
        if (!Toggles.autoEat || mc.player == null || mc.gameMode == null) return;
        LocalPlayer player = mc.player;
        if (player.getFoodData().getFoodLevel() >= Toggles.autoEatThreshold) return;
        if (player.isUsingItem()) return;
        var held = player.getMainHandItem();
        if (!held.isEmpty() && held.has(DataComponents.FOOD)) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
    }

    public static void clearKickReason() {
        lastKickReason = null;
    }

    public static String lastKickReason() {
        return lastKickReason;
    }

    /**
     * Whether the client is currently in a world with a live player. Used by modules that
     * would otherwise act on a null level.
     */
    public static boolean inWorld(Minecraft mc) {
        return mc.level != null && mc.player != null;
    }

    /** Convenience for the HUD: local food level, or -1 when not in a world. */
    public static int foodLevel(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return -1;
        }
        return player.getFoodData().getFoodLevel();
    }
}
