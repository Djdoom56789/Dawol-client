package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;

public final class AutoTotemModule {

    private AutoTotemModule() {}

    private static long nextActionMs = 0;

    public static void tick(Minecraft mc) {
        if (!Toggles.autoTotemEnabled) return;
        if (mc.player == null || mc.level == null) return;
        if (!mc.mouseHandler.isMouseGrabbed()) return;

        long now = System.currentTimeMillis();
        if (now < nextActionMs) return;

        Player player = mc.player;
        InventoryMenu menu = player.inventoryMenu;

        if (menu.getSlot(Inventory.SLOT_OFFHAND).getItem().is(Items.TOTEM_OF_UNDYING)) return;

        int totemSlot = -1;
        for (int slot = 9; slot <= 44; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.TOTEM_OF_UNDYING)) {
                totemSlot = slot;
                break;
            }
        }
        if (totemSlot < 0) return;

        mc.gameMode.handleContainerInput(
                menu.containerId, totemSlot,
                Inventory.SLOT_OFFHAND,
                ContainerInput.SWAP,
                player);

        nextActionMs = now + DelayRandomizer.sample(
                Toggles.autoTotemDelayMinMs, Toggles.autoTotemDelayMaxMs);
    }
}
