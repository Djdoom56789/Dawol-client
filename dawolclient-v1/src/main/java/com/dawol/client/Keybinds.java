package com.dawol.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Key bindings. Registered on the client entrypoint.
 *
 * <p>Bindings are deliberately ordinary client keybinds registered through the Fabric
 * key-mapping API, so they appear in the vanilla controls screen like any other mod.
 */
public final class Keybinds {

    /** Dedicated category so our binds are grouped in the controls list. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("dawolclient", "controls"));

    private static final String PREFIX = "key.dawolclient.";

    public static KeyMapping toggleEsp;
    public static KeyMapping toggleStash;
    public static KeyMapping toggleCombat;
    public static KeyMapping toggleThroughWalls;
    public static KeyMapping toggleTracers;
    public static KeyMapping clearStash;
    public static KeyMapping rescan;
    public static KeyMapping openSchematic;
    public static KeyMapping openConfig;

    private Keybinds() {
    }

    private static KeyMapping bind(String name, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(PREFIX + name, InputConstants.Type.KEYBOARD, defaultKey, CATEGORY));
    }

    public static void register() {
        toggleEsp = bind("toggle_esp", InputConstants.KEY_X);
        toggleStash = bind("toggle_stash", InputConstants.KEY_V);
        toggleCombat = bind("toggle_combat", InputConstants.KEY_R);
        toggleThroughWalls = bind("toggle_walls", InputConstants.KEY_B);
        toggleTracers = bind("toggle_tracers", InputConstants.KEY_G);
        clearStash = bind("clear_stash", InputConstants.KEY_N);
        rescan = bind("rescan", InputConstants.KEY_M);
        openSchematic = bind("open_schematic", InputConstants.KEY_L);
        openConfig = bind("open_config", InputConstants.KEY_RSHIFT);
    }

    /**
     * Drains queued key presses. Returns {@code true} if a re-scan was requested, so the
     * caller can schedule it for the next tick.
     */
    public static boolean poll() {
        boolean wantsRescan = false;

        while (toggleEsp.consumeClick()) {
            boolean enable = !(Toggles.espPlayers || Toggles.espMobs || Toggles.espItems);
            Toggles.espPlayers = enable;
            Toggles.espMobs = enable;
            Toggles.espItems = enable;
        }

        while (toggleStash.consumeClick()) {
            Toggles.stashFinder = !Toggles.stashFinder;
            if (Toggles.stashFinder) {
                wantsRescan = true;
            }
        }

        while (toggleCombat.consumeClick()) {
            Toggles.combatAssist = !Toggles.combatAssist;
            if (!Toggles.combatAssist) {
                CombatSystem.reset();
            }
        }

        // Tracers get their own key because they are the feature most often wanted on its
        // own, independent of the entity boxes.
        while (toggleTracers.consumeClick()) {
            Toggles.espTracers = !Toggles.espTracers;
        }

        // Same three fields the Through Walls module flips, so the key and the menu agree.
        while (toggleThroughWalls.consumeClick()) {
            boolean flipped = !Toggles.espThroughWalls;
            Toggles.espThroughWalls = flipped;
            Toggles.stashThroughWalls = flipped;
            Toggles.blockEspThroughWalls = flipped;
        }

        while (clearStash.consumeClick()) {
            StashFinder.reset();
        }

        while (rescan.consumeClick()) {
            wantsRescan = true;
        }

        return wantsRescan;
    }

    /**
     * Handles the overlay key. Kept separate from {@link #poll} because opening a screen
     * needs the client instance and should happen once per press.
     *
     * <p>No "is a screen already open" guard is needed: a key mapping only produces a
     * queued click while the game, rather than a screen, holds input focus.
     */
    public static void pollScreenKeys(Minecraft mc) {
        while (openConfig.consumeClick()) {
            mc.setScreenAndShow(new ModuleScreen(null));
        }

        while (openSchematic.consumeClick()) {
            mc.setScreenAndShow(new SchematicScreen(null));
        }
    }
}

