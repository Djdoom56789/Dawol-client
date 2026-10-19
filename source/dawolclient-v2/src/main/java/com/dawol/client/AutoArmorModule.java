package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortoughness.ArmorMaterial;

/**
 * AutoArmorModule — automatically equips the strongest available armor.
 *
 * On each client tick, for each of the four armor slots, compares the
 * currently equipped item against every item in the player's inventory.
 * If a better piece is found, swaps it using a standard double-click
 * shift+click sequence (vanilla container input — no packet injection).
 *
 * "Stronger" means higher combined defense + toughness. Netherite >
 * diamond > iron > chainmail > golden > leather.
 *
 * Delays all actions through DelayRandomizer so no two equip actions
 * happen on the same tick.
 *
 * Call from ClientTickEvents.END_CLIENT_TICK:
 *   AutoArmorModule.tick(mc);
 */
public final class AutoArmorModule {

    private AutoArmorModule() {}

    private static long nextActionMs = 0;

    public static void tick(Minecraft mc) {
        Toggles t = DawolClient.toggles();
        if (!t.autoArmorEnabled) return;
        if (mc.player == null || mc.level == null) return;
        if (!DawolClient.isAllowed(mc)) return;

        long now = System.currentTimeMillis();
        if (now < nextActionMs) return;

        Player player = mc.player;
        Inventory inv = player.getInventory();
        InventoryMenu menu = player.inventoryMenu;

        // Check each armor slot (head=5, chest=6, legs=7, feet=8 in InventoryMenu)
        int[] armorMenuSlots = {5, 6, 7, 8}; // head → feet
        EquipmentSlot[] eqSlots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (int si = 0; si < armorMenuSlots.length; si++) {
            int armorSlot  = armorMenuSlots[si];
            EquipmentSlot eq = eqSlots[si];

            ItemStack equipped = menu.getSlot(armorSlot).getItem();
            int equippedScore  = armorScore(equipped, eq);

            // Scan player inventory slots 9–44 (hotbar + main inv)
            // InventoryMenu: slots 9–35 = main inv rows, 36–44 = hotbar
            int bestSlot  = -1;
            int bestScore = equippedScore;
            for (int slot = 9; slot <= 44; slot++) {
                ItemStack s = menu.getSlot(slot).getItem();
                int score   = armorScore(s, eq);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot  = slot;
                }
            }

            if (bestSlot < 0) continue;

            // Shift-click the better piece — vanilla moves it to the armor slot
            mc.gameMode.handleInventoryMouseClick(
                    menu.containerId, bestSlot,
                    0, net.minecraft.world.inventory.ClickType.QUICK_MOVE,
                    player);

            nextActionMs = now + DelayRandomizer.sample(
                    t.autoArmorDelayMinMs, t.autoArmorDelayMaxMs);
            return; // one action per tick interval
        }
    }

    /**
     * Defense score for an ItemStack in a given slot.
     * Returns 0 for non-armor or wrong-slot items.
     */
    private static int armorScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof ArmorItem a)) return 0;
        if (a.getEquipmentSlot() != slot) return 0;
        // defense * 10 + toughness rounded
        return a.getDefense() * 10 + (int) (a.getToughness() * 10);
    }
}
