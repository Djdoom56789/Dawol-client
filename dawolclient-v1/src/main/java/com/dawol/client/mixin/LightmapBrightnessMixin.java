package com.dawol.client.mixin;

import com.dawol.client.Toggles;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces every brightness lookup to 1.0 when Fullbright is on, bypassing the
 * dimension-specific and lightmap-computed values the normal path produces.
 */
@Mixin(Lightmap.class)
public class LightmapBrightnessMixin {

    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private static void onGetBrightness(DimensionType dimensionType, int lightLevel,
            CallbackInfoReturnable<Float> cir) {
        if (Toggles.fullbright) {
            cir.setReturnValue(1.0f);
        }
    }
}
