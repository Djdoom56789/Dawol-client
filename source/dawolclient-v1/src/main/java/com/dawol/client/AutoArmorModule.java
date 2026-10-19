package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class AutoArmorModule {

    private AutoArmorModule() {}

    private static long nextActionMs = 0;

    public static void tick(Minecraft mc) {
        if (!Toggles.autoArmorEnabled) return;
        if (mc.player == null || mc.level == null) return;
        if (!mc.mouseHandler.isMouseGrabbed()) return;

        long now = System.currentTimeMillis();
        if (now < nextActionMs) return;

        Player player = mc.player;
        InventoryMenu menu = player.inventoryMenu;

        int[] armorMenuSlots = {5, 6, 7, 8};
        EquipmentSlot[] eqSlots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (int si = 0; si < armorMenuSlots.length; si++) {
            int armorSlot  = armorMenuSlots[si];
            EquipmentSlot eq = eqSlots[si];

            ItemStack equipped = menu.getSlot(armorSlot).getItem();
            int equippedScore  = armorScore(equipped, eq);

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

            mc.gameMode.handleContainerInput(
                    menu.containerId, bestSlot,
                    0, ContainerInput.QUICK_MOVE,
                    player);

            nextActionMs = now + DelayRandomizer.sample(
                    Toggles.autoArmorDelayMinMs, Toggles.autoArmorDelayMaxMs);
            return;
        }
    }

    private static int armorScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0;
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return (eq != null && eq.slot() == slot) ? 1 : 0;
    }
}
