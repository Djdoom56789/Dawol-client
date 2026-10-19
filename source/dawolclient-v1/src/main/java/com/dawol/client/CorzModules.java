package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Modules adapting Corz Client's feature set to this client's constraints.
 *
 * <p>Every method here uses public vanilla APIs only. No mixin, no class transformer, no
 * packet construction — the one interaction that touches the server (Auto Totem) goes
 * through {@code MultiPlayerGameMode.handleContainerInput}, which emits the ordinary
 * {@code ServerboundContainerClickPacket} the server already expects from any player
 * reorganising their inventory by hand.
 */
public final class CorzModules {

        /**
     * Gate for inventory actions. The delay is drawn fresh from the configured window on
     * every action rather than being a fixed interval, so the swap timing does not form a
     * constant pattern.
     */
    private static final DelayRandomizer ACTION_GATE = DelayRandomizer.create();

    private CorzModules() {
    }

    /** Call once per client tick. */
    public static void tick(Minecraft mc) {
        autoTotem(mc);
        chestStealer(mc);
    }

    /** Milliseconds until Auto Totem may next act, for the HUD and tests. */
    public static long nextActionInMillis() {
        return ACTION_GATE.remainingMillis();
    }

    // --- Totem Counter (Corz: "Totem Counter") ---

    /** How many totems sit in the offhand right now. */
    public static int offhandTotems(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0;
        }
        ItemStack offhand = player.getInventory().getItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND);
        return offhand.getItem() == Items.TOTEM_OF_UNDYING ? offhand.getCount() : 0;
    }

    /** Total totems carried anywhere in the inventory. */
    public static int totalTotems(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // --- Chest Stealer ---

    /**
     * When a container screen is open, shift-clicks every non-empty slot from
     * the container side into the player's inventory. Rate-limited.
     */
    private static void chestStealer(Minecraft mc) {
        if (!Toggles.chestStealer || mc.gameMode == null || !ACTION_GATE.readyAuto()) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        AbstractContainerMenu menu = player.containerMenu;
        // player.containerMenu is the bare inventory menu when no container is open.
        // We have a container open when the screen is up (mouse is not grabbed).
        if (mc.mouseHandler.isMouseGrabbed()) return;

        Inventory inv = player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container == inv) continue; // skip player-side slots
            if (slot.getItem().isEmpty()) continue;
            mc.gameMode.handleContainerInput(
                    menu.containerId, slot.index, 0,
                    net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
            return; // one per gate tick for humanised pacing
        }
    }

    // --- Auto Totem (Corz: "Auto Totem" / "Auto Inventory Totem") ---

    /**
     * Moves a totem into the offhand when it is empty.
     *
     * <p>Uses the {@code SWAP} container action with the offhand slot index, which is the
     * exact action the vanilla client sends for the F-key offhand swap. Rate limited, and
     * only fires when the offhand no longer holds a totem, so it does not spam the server.
     */
    private static void autoTotem(Minecraft mc) {
        if (!Toggles.autoTotem || mc.gameMode == null || !ACTION_GATE.readyAuto()) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || player.containerMenu == null) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;

        // Already covered.
        if (offhandTotems(mc) > 0) {
            return;
        }

        // Find a totem in the player's own inventory and swap it into the offhand. The slot's
        // backing container is what decides ownership: InventoryMenu has 46 slots (result,
        // 2x2 grid, 4 armour, 36 main, 1 offhand) while Inventory.getContainerSize() is 41, so
        // a numeric bound stops short of the hotbar and still scans the result/grid/armour
        // slots. With a chest open player.containerMenu is the chest menu, where the same
        // bound could pull a totem straight out of the chest.
        Inventory inventory = player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container != inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getItem() != Items.TOTEM_OF_UNDYING) {
                continue;
            }

            // SWAP with button = offhand slot index is the F-key offhand exchange.
            mc.gameMode.handleContainerInput(
                    menu.containerId,
                    slot.index,
                    Inventory.SLOT_OFFHAND,
                    ContainerInput.SWAP,
                    player);
            // ACTION_GATE.readyAuto() already scheduled the next randomised window.
            return;
        }
    }
}