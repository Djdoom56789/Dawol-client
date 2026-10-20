package com.dawol.client.mixin;

import com.dawol.client.Toggles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels knockback on the local player when Velocity Cancel is enabled.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(
        method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
        at = @At("HEAD"), cancellable = true, require = 0
    )
    private void onKnockback6(double strength, double x, double z, DamageSource source,
            float f, boolean bl, CallbackInfo ci) {
        cancelIfLocalPlayer(ci);
    }

    @Inject(
        method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;F)V",
        at = @At("HEAD"), cancellable = true, require = 0
    )
    private void onKnockback5(double strength, double x, double z, DamageSource source,
            float f, CallbackInfo ci) {
        cancelIfLocalPlayer(ci);
    }

    private void cancelIfLocalPlayer(CallbackInfo ci) {
        if (!Toggles.velocityCancel) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player == (Object) this) {
            ci.cancel();
        }
    }
}
