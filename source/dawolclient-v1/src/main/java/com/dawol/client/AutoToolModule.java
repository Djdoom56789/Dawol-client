package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoToolModule {

    private AutoToolModule() {}

    public static void tick(Minecraft mc) {
        if (!Toggles.autoToolEnabled) return;
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockState state = mc.level.getBlockState(bhr.getBlockPos());
        if (state.isAir()) return;

        Player player = mc.player;
        Inventory inv  = player.getInventory();

        int bestSlot  = 0;
        float bestSpeed = 0f;
        for (int slot = 0; slot < 9; slot++) {
            float speed = destroySpeed(inv.getItem(slot), state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot  = slot;
            }
        }

        float currentSpeed = destroySpeed(player.getMainHandItem(), state);
        if (bestSpeed > currentSpeed) {
            inv.setSelectedSlot(bestSlot);
        }
    }

    private static float destroySpeed(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return 1.0f;
        return stack.getDestroySpeed(state);
    }
}
