package com.dawol.client;

import java.util.List;

/**
 * The concrete feature registry, grouped into the categories the UI renders as tabs.
 */
public final class Modules {

    private Modules() {
    }

    private static Module of(String name, Module.Category category, String description,
                             java.util.function.BooleanSupplier getter,
                             java.util.function.Consumer<Boolean> setter) {
        Module module = new Module(name, category, description) {
            @Override
            public boolean isEnabled() {
                return getter.getAsBoolean();
            }

            @Override
            public void setEnabled(boolean enabled) {
                setter.accept(enabled);
            }
        };
        return Module.register(module);
    }

    /** Registers everything. Called once from the client entrypoint. */
    public static void register() {
        if (Module.all().size() > 0) {
            return;
        }

        // --- Render ---
        of("Player ESP", Module.Category.RENDER,
                "Draw colored boxes around other players",
                () -> Toggles.espPlayers, v -> Toggles.espPlayers = v);
        of("Mob ESP", Module.Category.RENDER,
                "Draw boxes around hostile mobs",
                () -> Toggles.espMobs, v -> Toggles.espMobs = v);
        of("Item ESP", Module.Category.RENDER,
                "Draw boxes around dropped items on the ground",
                () -> Toggles.espItems, v -> Toggles.espItems = v);
        of("Entity Tracers", Module.Category.RENDER,
                "Draw lines from your crosshair to every tracked entity",
                () -> Toggles.espTracers, v -> Toggles.espTracers = v);
        of("Health Bars", Module.Category.RENDER,
                "Show a health bar above living entities",
                () -> Toggles.espHealthBar, v -> Toggles.espHealthBar = v);
        of("Nametags", Module.Category.RENDER,
                "Show name and distance text above entities",
                () -> Toggles.espLabels, v -> Toggles.espLabels = v);
        of("Block ESP", Module.Category.RENDER,
                "Highlight selected blocks. Open Block Picker (menu) then press Add common ores",
                () -> Toggles.blockEsp, v -> Toggles.blockEsp = v);
        of("Fullbright", Module.Category.RENDER,
                "Force maximum brightness everywhere — no torches needed",
                () -> Toggles.fullbright, v -> Toggles.fullbright = v);
        of("Zoom", Module.Category.RENDER,
                "Reduce FOV to zoom in like a spyglass. Toggle to activate",
                () -> Toggles.zoom, v -> Toggles.zoom = v);
        of("Anti-Trap", Module.Category.RENDER,
                "Hide armour stands so cage traps are visible through them",
                () -> Toggles.antiTrap, v -> Toggles.antiTrap = v);

        // --- World ---
        of("Stash Finder", Module.Category.WORLD,
                "Score each chunk by container count and show tracers to stash locations",
                () -> Toggles.stashFinder, v -> Toggles.stashFinder = v);
        of("Stash: show all chunks", Module.Category.WORLD,
                "Also show chunks below the stash threshold, not just confirmed stashes",
                () -> Toggles.stashShowAllChunks, v -> Toggles.stashShowAllChunks = v);
        of("Base Finder", Module.Category.WORLD,
                "Detect player bases by structural block density: bricks, obsidian, planks",
                () -> Toggles.baseFinder, v -> Toggles.baseFinder = v);
        of("Chunk Finder", Module.Category.WORLD,
                "Master switch for chunk highlighting (new chunks and ore-dense chunks)",
                () -> Toggles.chunkEsp, v -> Toggles.chunkEsp = v);
        of("New Chunks", Module.Category.WORLD,
                "Cyan outline on chunks first loaded in the last 2 minutes — base-finding signal",
                () -> Toggles.chunkNewHighlight, v -> Toggles.chunkNewHighlight = v);
        of("Dense Ore Chunks", Module.Category.WORLD,
                "Yellow outline on chunks with unusually high ore counts",
                () -> Toggles.chunkDensityHighlight, v -> Toggles.chunkDensityHighlight = v);

        // --- Combat ---
        of("Combat Assist", Module.Category.COMBAT,
                "Auto-swing at the nearest valid target while holding attack",
                () -> Toggles.combatAssist, v -> Toggles.combatAssist = v);
        of("PVP Mode", Module.Category.COMBAT,
                "Target other players as well as mobs when Combat Assist is on",
                () -> Toggles.pvpTargetPlayers, v -> Toggles.pvpTargetPlayers = v);
        of("Velocity Cancel", Module.Category.COMBAT,
                "Block all incoming knockback on your player",
                () -> Toggles.velocityCancel, v -> Toggles.velocityCancel = v);
        of("No Fall", Module.Category.COMBAT,
                "Cancel fall damage — land from any height without taking damage",
                () -> Toggles.noFall, v -> Toggles.noFall = v);
        of("Rotation", Module.Category.COMBAT,
                "Turn your camera toward the combat target within per-tick angle caps",
                () -> Toggles.combatSilentRotate, v -> Toggles.combatSilentRotate = v);
        of("Reach Gate", Module.Category.COMBAT,
                "Skip swings when the target is beyond max reach (default 3 blocks)",
                () -> Toggles.combatReachGate, v -> Toggles.combatReachGate = v);
        of("Line of Sight", Module.Category.COMBAT,
                "Skip targets that have no clear line of sight to you",
                () -> Toggles.combatLineOfSightGate, v -> Toggles.combatLineOfSightGate = v);
        of("Target ESP", Module.Category.COMBAT,
                "Highlight the current combat target with a bright outline",
                () -> Toggles.targetEsp, v -> Toggles.targetEsp = v);
        of("Cooldown Gate", Module.Category.COMBAT,
                "Only swing at full attack charge — avoids wasted hits",
                () -> Toggles.combatCooldownGate, v -> Toggles.combatCooldownGate = v);

        // --- Player ---
        of("Auto Totem", Module.Category.PLAYER,
                "Automatically keep a totem of undying in your offhand",
                () -> Toggles.autoTotem, v -> Toggles.autoTotem = v);
        of("Auto Sprint", Module.Category.PLAYER,
                "Always sprint when food level is high enough",
                () -> Toggles.autoSprint, v -> Toggles.autoSprint = v);
        of("Chest Stealer", Module.Category.PLAYER,
                "Automatically shift-click all loot out of any open container",
                () -> Toggles.chestStealer, v -> Toggles.chestStealer = v);
        of("Totem Counter", Module.Category.PLAYER,
                "Show how many totems of undying you are carrying on the HUD",
                () -> Toggles.totemCounter, v -> Toggles.totemCounter = v);
        of("Auto Reconnect", Module.Category.PLAYER,
                "Automatically reconnect to the server after being disconnected",
                () -> Toggles.autoReconnect, v -> Toggles.autoReconnect = v);
        of("Anti-Kick", Module.Category.PLAYER,
                "Prevent idle kicks by sending periodic movements",
                () -> Toggles.antiKick, v -> Toggles.antiKick = v);
        of("Auto Respawn", Module.Category.PLAYER,
                "Respawn instantly on death without clicking the respawn button",
                () -> Toggles.autoRespawn, v -> Toggles.autoRespawn = v);
        of("Auto Eat", Module.Category.PLAYER,
                "Eat the food in your hand automatically when hunger drops low",
                () -> Toggles.autoEat, v -> Toggles.autoEat = v);

        // --- Misc ---
        of("Schematic Builder", Module.Category.MISC,
                "Load a schematic and place it block by block automatically (CorzClient feature)",
                () -> Toggles.schematicBuilder, v -> Toggles.schematicBuilder = v);
        of("Through Walls", Module.Category.MISC,
                "Draw all ESP boxes and tracers through terrain (master switch)",
                () -> Toggles.espThroughWalls, v -> {
                    Toggles.espThroughWalls = v;
                    Toggles.stashThroughWalls = v;
                    Toggles.blockEspThroughWalls = v;
                });
        of("Status HUD", Module.Category.MISC,
                "Show a summary of all active modules on your screen",
                () -> Toggles.statusHud, v -> Toggles.statusHud = v);
    }


    public static List<Module> all() {
        return Module.all();
    }

    public static List<Module> inCategory(Module.Category category) {
        return Module.inCategory(category);
    }
}
