package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * AutoTotemModule — keeps a Totem of Undying in the offhand slot.
 *
 * On each client tick: if the offhand is empty or not a totem, and a
 * totem exists anywhere in the player's inventory, moves it to the
 * offhand using a vanilla F-key swap (slot 40 = offhand in InventoryMenu,
 * ClickType.SWAP with button=40).
 *
 * This replaces the hollow AutoTotem stub in CorzModules.
 * Call from ClientTickEvents.END_CLIENT_TICK:
 *   AutoTotemModule.tick(mc);
 *
 * Remove the hollow stub from CorzModules when integrating.
 */
public final class AutoTotemModule {

    private AutoTotemModule() {}

    private static long nextActionMs = 0;
    /** Offhand slot in InventoryMenu. */
    private static final int OFFHAND_SLOT = 45; // 26.3: slot 45 in player's InventoryMenu

    public static void tick(Minecraft mc) {
        Toggles t = DawolClient.toggles();
        if (!t.autoTotemEnabled) return;
        if (mc.player == null || mc.level == null) return;
        if (!DawolClient.isAllowed(mc)) return;

        long now = System.currentTimeMillis();
        if (now < nextActionMs) return;

        Player player = mc.player;
        InventoryMenu menu = player.inventoryMenu;

        // Check offhand (slot 45)
        ItemStack offhand = menu.getSlot(OFFHAND_SLOT).getItem();
        if (offhand.is(Items.TOTEM_OF_UNDYING)) return; // already good

        // Find a totem in hotbar or main inventory (slots 9–44 in InventoryMenu)
        int totemSlot = -1;
        for (int slot = 9; slot <= 44; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.TOTEM_OF_UNDYING)) {
                totemSlot = slot;
                break;
            }
        }
        if (totemSlot < 0) return; // no totem available

        // Move totem to the hotbar first if it's in main inv (slots 9–35),
        // then F-swap from hotbar. Or just shift-click to offhand directly.
        // Simplest: SWAP button=40 means "swap this slot with offhand".
        mc.gameMode.handleInventoryMouseClick(
                menu.containerId, totemSlot,
                40,                          // button 40 = offhand swap (F key)
                ClickType.SWAP,
                player);

        nextActionMs = now + DelayRandomizer.sample(
                t.autoTotemDelayMinMs, t.autoTotemDelayMaxMs);
    }
}
