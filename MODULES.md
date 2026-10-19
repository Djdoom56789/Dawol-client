# DawolClient Module List

All features from KryptonClient, CorzClient, and GoobaClient merged into one dark-green Fabric 26.3 client.

## RENDER
| Module | What it does |
|---|---|
| Player ESP | Colored boxes around other players with health bars and name tags |
| Mob ESP | Boxes around hostile mobs |
| Item ESP | Boxes around dropped items |
| Entity Tracers | Screen-anchored lines to each tracked entity |
| Health Bars | Health bar drawn above living entities |
| Nametags | Name and distance text above entities |
| Block ESP | Boxes and tracers for blocks you select in the Block Picker. Use "Add common ores" to auto-select all ores |
| Fullbright | Forces max light level via mixin — no gamma cap |
| Zoom | Reduces FOV to 1/4 for a telescopic view (toggle in module list) |
| Anti-Trap | Hides armour stands so cage traps are visible through them |

## WORLD
| Module | What it does |
|---|---|
| Stash Finder | Scores each chunk by container count; shows tracers to stash locations |
| Stash: show all chunks | Shows chunks below the stash threshold too |
| Base Finder | Scores chunks by structural block density (bricks, planks, obsidian) to find player bases |
| Chunk Finder | Master switch for new-chunk and dense-ore chunk highlighting |
| New Chunks | Cyan column outline on chunks first received within the last 2 minutes |
| Dense Ore Chunks | Yellow column outline on chunks with high ore counts |

## COMBAT
| Module | What it does |
|---|---|
| Combat Assist | Auto-swings at the nearest valid target while attack is held |
| PVP Mode | Targets players in addition to mobs when Combat Assist is on |
| Velocity Cancel | Cancels incoming knockback on your player via mixin |
| No Fall | Cancels fall damage via mixin |
| Rotation | Turns camera toward combat target within per-tick caps |
| Reach Gate | Skips swings when target is beyond max reach (default 3 blocks) |
| Line of Sight | Skips targets with no clear line of sight |
| Target ESP | Highlights the current combat target |
| Cooldown Gate | Only swings at full attack charge |

## PLAYER
| Module | What it does |
|---|---|
| Auto Totem | Keeps a totem of undying in your offhand automatically |
| Auto Sprint | Always sprints when food level allows |
| Chest Stealer | Shift-clicks all loot out of open containers automatically |
| Totem Counter | Shows carried totem count on the HUD |
| Auto Reconnect | Reconnects automatically after a disconnect |
| Anti-Kick | Cancels idle kicks by sending periodic movements |
| Auto Respawn | Respawns immediately on death |
| Auto Eat | Eats held food when hunger drops below threshold |

## MISC
| Module | What it does |
|---|---|
| Schematic Builder | Loads an NBT/litematica schematic and places it block-by-block (from CorzClient) |
| Through Walls | Master switch: draws all ESP geometry through terrain |
| Status HUD | On-screen summary of active modules |

---
*Built for Minecraft 26.3 • Fabric Loader 0.19.5 • Java 25*
