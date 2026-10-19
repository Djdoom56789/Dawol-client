package com.dawol.client.mixin;

import com.dawol.client.Toggles;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reduces FOV while Zoom is active. Equivalent to Krypton's GameRendererMixin.
 */
@Mixin(GameRenderer.class)
public class ZoomMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float partialTick, boolean changingFov,
            CallbackInfoReturnable<Double> cir) {
        if (Toggles.zoom) {
            cir.setReturnValue(cir.getReturnValue() / Toggles.zoomFactor);
        }
    }
}
