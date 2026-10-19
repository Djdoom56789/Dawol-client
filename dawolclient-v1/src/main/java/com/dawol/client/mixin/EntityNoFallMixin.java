package com.dawol.client.mixin;

import com.dawol.client.Toggles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cancels fall damage on the local player when No Fall is enabled.
 */
@Mixin(Entity.class)
public class EntityNoFallMixin {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void onCauseFallDamage(double fallDistance, float multiplier, DamageSource source,
            CallbackInfoReturnable<Boolean> cir) {
        if (!Toggles.noFall) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player == (Object) this) {
            cir.setReturnValue(false);
        }
    }
}
