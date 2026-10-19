package com.dawol.client;

import net.minecraft.client.Minecraft;

/**
 * Raises the world brightness ceiling to the top of the vanilla gamma range.
 *
 * <p>Implemented purely through {@code Options.gamma()}, which is a public accessor in
 * 26.3 returning a normal {@code OptionInstance<Double>}. That matters: the usual way to
 * write this module is a mixin against the lightmap, and this avoids it entirely. The mod
 * therefore still ships with zero mixins.
 *
 * <p>The option's validator is {@code OptionInstance.UnitDouble}, which accepts only
 * {@code [0, 1]} and silently keeps the current value for anything outside it. This module
 * therefore pins gamma at the upper bound of that range — the brightest setting the
 * vanilla slider offers. Lifting the lightmap past it is not reachable through this API
 * without a mixin, which this mod does not use. The previous value is saved and restored
 * when the module is switched off, so toggling never permanently edits the user's options
 * file.
 */
public final class FullbrightModule {

    /** Brightest gamma {@code Options.gamma()} will accept; larger values are discarded. */
    private static final double MAX_GAMMA = 1.0D;

    private static boolean applied = false;

    private FullbrightModule() {
    }

    /** Call once per client tick. Cheap: it no-ops unless the desired state changed. */
    public static void tick(Minecraft mc) {
        if (mc.options == null) {
            return;
        }

        // Clamped so the set is always one the option will actually accept.
        double target = Math.clamp(Toggles.fullbrightGamma, 0.0D, MAX_GAMMA);

        if (Toggles.fullbright && !applied) {
            Toggles.savedGamma = mc.options.gamma().get();
            mc.options.gamma().set(target);
            applied = true;
        } else if (!Toggles.fullbright && applied) {
            mc.options.gamma().set(Toggles.savedGamma);
            applied = false;
        } else if (Toggles.fullbright && applied) {
            // Another mod or the options screen may have reset it; keep it pinned.
            double current = mc.options.gamma().get();
            if (Math.abs(current - target) > 0.001D) {
                mc.options.gamma().set(target);
            }
        }
    }

    /** Restores the stored gamma, used when the module is force-disabled. */
    public static void restore(Minecraft mc) {
        if (applied && mc.options != null) {
            mc.options.gamma().set(Toggles.savedGamma);
            applied = false;
        }
    }
}
