package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * AutoToolModule — switches to the most efficient hotbar tool when the
 * player is looking at a breakable block.
 *
 * Only swaps within the hotbar (slots 0–8) because a swap to the main
 * inventory would require a container click and is visually disorienting.
 * If the current slot is already the fastest, no swap occurs.
 *
 * The restore-on-stop behaviour (switching back after breaking) is omitted
 * because there is no reliable "player stopped mining" signal without a
 * mixin; the user can just switch back manually.
 *
 * Call from ClientTickEvents.END_CLIENT_TICK:
 *   AutoToolModule.tick(mc);
 */
public final class AutoToolModule {

    private AutoToolModule() {}

    public static void tick(Minecraft mc) {
        Toggles t = DawolClient.toggles();
        if (!t.autoToolEnabled) return;
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockState state = mc.level.getBlockState(bhr.getBlockPos());
        if (state.isAir()) return;

        Player player = mc.player;
        Inventory inv  = player.getInventory();

        int bestSlot  = inv.selected;
        float bestSpeed = destroySpeed(inv.getItem(bestSlot), state);

        for (int slot = 0; slot < 9; slot++) {
            float speed = destroySpeed(inv.getItem(slot), state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot  = slot;
            }
        }

        if (bestSlot != inv.selected) {
            inv.selected = bestSlot;
        }
    }

    private static float destroySpeed(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return 1.0f;
        return stack.getDestroySpeed(state);
    }
}
