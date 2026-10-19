# DawolClient v2 — Merge Instructions

Copy every `.java` file from `src/main/java/com/dawol/client/` into
`gtrace/src/main/java/com/dawol/client/`. Then apply the diffs below to
the existing files.

---

## 1. Toggles.java — add these fields

Add inside the class body (anywhere near the existing module flags):

```java
// ── StorageESP ────────────────────────────────────────────────
public boolean storageEspEnabled       = false;
public boolean storageEspTracers       = true;
public boolean storageEspThroughWalls  = true;

// ── HoleESP ──────────────────────────────────────────────────
public boolean holeEspEnabled          = false;
public boolean holeEspTracers          = false;
public int     holeEspRadius           = 8;

// ── OreSimModule ──────────────────────────────────────────────
public boolean oreSimEnabled           = false;
public boolean oreSimScan              = true;
public boolean oreSimPredict           = false; // single-player only
public boolean oreSimTracers           = false;

// ── AutoArmor ────────────────────────────────────────────────
public boolean autoArmorEnabled        = false;
public long    autoArmorDelayMinMs     = 200;
public long    autoArmorDelayMaxMs     = 600;

// ── AutoTotem (replaces hollow CorzModules stub) ─────────────
// NOTE: remove the autoTotemEnabled field from CorzModules if it exists.
public boolean autoTotemEnabled        = false;
public long    autoTotemDelayMinMs     = 50;
public long    autoTotemDelayMaxMs     = 200;

// ── AntiTrap (was hollow in CorzModules) ─────────────────────
// NOTE: remove hollow antiTrap field from CorzModules / Toggles if it exists.
public long    antiTrapDelayMinMs      = 80;
public long    antiTrapDelayMaxMs      = 250;
// antiTrapEnabled already exists — keep it, just wire it to AntiTrapModule.

// ── TriggerBot ────────────────────────────────────────────────
public boolean triggerBotEnabled       = false;
public boolean triggerBotAttackPlayers = false;
public float   triggerBotCooldownThreshold = 0.85f;
public long    triggerBotDelayMinMs    = 80;
public long    triggerBotDelayMaxMs    = 300;

// ── AutoTool ─────────────────────────────────────────────────
public boolean autoToolEnabled         = false;
```

---

## 2. Config.java — add save/load pairs for every new flag

Follow the exact pattern used for the existing flags. Add one `put`/`get`
pair per field, keeping save and load symmetric. Example:

```java
// In save():
props.put("storageEspEnabled",       String.valueOf(t.storageEspEnabled));
props.put("storageEspTracers",       String.valueOf(t.storageEspTracers));
props.put("storageEspThroughWalls",  String.valueOf(t.storageEspThroughWalls));
props.put("holeEspEnabled",          String.valueOf(t.holeEspEnabled));
props.put("holeEspTracers",          String.valueOf(t.holeEspTracers));
props.put("holeEspRadius",           String.valueOf(t.holeEspRadius));
props.put("oreSimEnabled",           String.valueOf(t.oreSimEnabled));
props.put("oreSimScan",              String.valueOf(t.oreSimScan));
props.put("oreSimPredict",           String.valueOf(t.oreSimPredict));
props.put("oreSimTracers",           String.valueOf(t.oreSimTracers));
props.put("autoArmorEnabled",        String.valueOf(t.autoArmorEnabled));
props.put("autoArmorDelayMinMs",     String.valueOf(t.autoArmorDelayMinMs));
props.put("autoArmorDelayMaxMs",     String.valueOf(t.autoArmorDelayMaxMs));
props.put("autoTotemEnabled",        String.valueOf(t.autoTotemEnabled));
props.put("autoTotemDelayMinMs",     String.valueOf(t.autoTotemDelayMinMs));
props.put("autoTotemDelayMaxMs",     String.valueOf(t.autoTotemDelayMaxMs));
props.put("antiTrapDelayMinMs",      String.valueOf(t.antiTrapDelayMinMs));
props.put("antiTrapDelayMaxMs",      String.valueOf(t.antiTrapDelayMaxMs));
props.put("triggerBotEnabled",       String.valueOf(t.triggerBotEnabled));
props.put("triggerBotAttackPlayers", String.valueOf(t.triggerBotAttackPlayers));
props.put("triggerBotCooldownThreshold", String.valueOf(t.triggerBotCooldownThreshold));
props.put("triggerBotDelayMinMs",    String.valueOf(t.triggerBotDelayMinMs));
props.put("triggerBotDelayMaxMs",    String.valueOf(t.triggerBotDelayMaxMs));
props.put("autoToolEnabled",         String.valueOf(t.autoToolEnabled));

// In load():
if (props.containsKey("storageEspEnabled"))      t.storageEspEnabled      = Boolean.parseBoolean(props.getProperty("storageEspEnabled"));
if (props.containsKey("storageEspTracers"))      t.storageEspTracers      = Boolean.parseBoolean(props.getProperty("storageEspTracers"));
if (props.containsKey("storageEspThroughWalls")) t.storageEspThroughWalls = Boolean.parseBoolean(props.getProperty("storageEspThroughWalls"));
if (props.containsKey("holeEspEnabled"))         t.holeEspEnabled         = Boolean.parseBoolean(props.getProperty("holeEspEnabled"));
if (props.containsKey("holeEspTracers"))         t.holeEspTracers         = Boolean.parseBoolean(props.getProperty("holeEspTracers"));
if (props.containsKey("holeEspRadius"))          t.holeEspRadius          = Integer.parseInt(props.getProperty("holeEspRadius"));
if (props.containsKey("oreSimEnabled"))          t.oreSimEnabled          = Boolean.parseBoolean(props.getProperty("oreSimEnabled"));
if (props.containsKey("oreSimScan"))             t.oreSimScan             = Boolean.parseBoolean(props.getProperty("oreSimScan"));
if (props.containsKey("oreSimPredict"))          t.oreSimPredict          = Boolean.parseBoolean(props.getProperty("oreSimPredict"));
if (props.containsKey("oreSimTracers"))          t.oreSimTracers          = Boolean.parseBoolean(props.getProperty("oreSimTracers"));
if (props.containsKey("autoArmorEnabled"))       t.autoArmorEnabled       = Boolean.parseBoolean(props.getProperty("autoArmorEnabled"));
if (props.containsKey("autoArmorDelayMinMs"))    t.autoArmorDelayMinMs    = Long.parseLong(props.getProperty("autoArmorDelayMinMs"));
if (props.containsKey("autoArmorDelayMaxMs"))    t.autoArmorDelayMaxMs    = Long.parseLong(props.getProperty("autoArmorDelayMaxMs"));
if (props.containsKey("autoTotemEnabled"))       t.autoTotemEnabled       = Boolean.parseBoolean(props.getProperty("autoTotemEnabled"));
if (props.containsKey("autoTotemDelayMinMs"))    t.autoTotemDelayMinMs    = Long.parseLong(props.getProperty("autoTotemDelayMinMs"));
if (props.containsKey("autoTotemDelayMaxMs"))    t.autoTotemDelayMaxMs    = Long.parseLong(props.getProperty("autoTotemDelayMaxMs"));
if (props.containsKey("antiTrapDelayMinMs"))     t.antiTrapDelayMinMs     = Long.parseLong(props.getProperty("antiTrapDelayMinMs"));
if (props.containsKey("antiTrapDelayMaxMs"))     t.antiTrapDelayMaxMs     = Long.parseLong(props.getProperty("antiTrapDelayMaxMs"));
if (props.containsKey("triggerBotEnabled"))          t.triggerBotEnabled          = Boolean.parseBoolean(props.getProperty("triggerBotEnabled"));
if (props.containsKey("triggerBotAttackPlayers"))    t.triggerBotAttackPlayers    = Boolean.parseBoolean(props.getProperty("triggerBotAttackPlayers"));
if (props.containsKey("triggerBotCooldownThreshold")) t.triggerBotCooldownThreshold = Float.parseFloat(props.getProperty("triggerBotCooldownThreshold"));
if (props.containsKey("triggerBotDelayMinMs"))   t.triggerBotDelayMinMs   = Long.parseLong(props.getProperty("triggerBotDelayMinMs"));
if (props.containsKey("triggerBotDelayMaxMs"))   t.triggerBotDelayMaxMs   = Long.parseLong(props.getProperty("triggerBotDelayMaxMs"));
if (props.containsKey("autoToolEnabled"))        t.autoToolEnabled        = Boolean.parseBoolean(props.getProperty("autoToolEnabled"));
```

---

## 3. Module.java — Category enum: no new categories needed

All new modules fit in the existing five: RENDER, WORLD, PLAYER, COMBAT, MISC.

| Module         | Category |
|----------------|----------|
| StorageESP     | RENDER   |
| HoleESP        | RENDER   |
| OreSimModule   | WORLD    |
| AutoArmor      | PLAYER   |
| AutoTotem      | PLAYER   |
| AntiTrap       | PLAYER   |
| TriggerBot     | COMBAT   |
| AutoTool       | PLAYER   |

---

## 4. Modules.java — add to register()

```java
register(new Module("Storage ESP",   Module.Category.RENDER,  "Box + tracer for chests, barrels, shulkers and more.",
        t -> t.storageEspEnabled, (t, v) -> t.storageEspEnabled = v));

register(new Module("Hole ESP",      Module.Category.RENDER,  "Highlights 1×1 holes for crystal PvP. Cyan = bedrock-bounded.",
        t -> t.holeEspEnabled,    (t, v) -> t.holeEspEnabled = v));

register(new Module("Ore Sim",       Module.Category.WORLD,   "Scan + predict ancient debris in the Nether.",
        t -> t.oreSimEnabled,     (t, v) -> t.oreSimEnabled = v));

register(new Module("Auto Armor",    Module.Category.PLAYER,  "Equips the best available armor. Humanised.",
        t -> t.autoArmorEnabled,  (t, v) -> t.autoArmorEnabled = v));

register(new Module("Auto Totem",    Module.Category.PLAYER,  "Keeps a Totem of Undying in the offhand.",
        t -> t.autoTotemEnabled,  (t, v) -> t.autoTotemEnabled = v));

register(new Module("Anti Trap",     Module.Category.PLAYER,  "Breaks nearby armor stands used to trap you.",
        t -> t.antiTrapEnabled,   (t, v) -> t.antiTrapEnabled = v));

register(new Module("Trigger Bot",   Module.Category.COMBAT,  "Left-clicks when an entity is under the crosshair.",
        t -> t.triggerBotEnabled, (t, v) -> t.triggerBotEnabled = v));

register(new Module("Auto Tool",     Module.Category.PLAYER,  "Switches to the fastest hotbar tool for the targeted block.",
        t -> t.autoToolEnabled,   (t, v) -> t.autoToolEnabled = v));
```

---

## 5. DawolClient.java — add hooks

### In the event registration block:

```java
// StorageESP render + chunk tracking
LevelRenderEvents.BEFORE_GIZMOS.register(ctx ->
        StorageESP.render(ctx, tracerAnchor));
ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
        StorageESP.scanChunk(chunk.getPos().x, chunk.getPos().z));
ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
        StorageESP.removeChunk(chunk.getPos().x, chunk.getPos().z));

// HoleESP render
LevelRenderEvents.BEFORE_GIZMOS.register(ctx ->
        HoleESP.render(ctx, tracerAnchor));

// OreSimModule render + chunk tracking
LevelRenderEvents.BEFORE_GIZMOS.register(ctx ->
        OreSimModule.render(ctx, tracerAnchor));
ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
        OreSimModule.scanChunk(chunk.getPos().x, chunk.getPos().z));
ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
        OreSimModule.removeChunk(chunk.getPos().x, chunk.getPos().z));
```

### In the END_CLIENT_TICK lambda (after the existing module calls):

```java
AutoArmorModule.tick(mc);
AutoTotemModule.tick(mc);
AntiTrapModule.tick(mc);
TriggerBotModule.tick(mc);
AutoToolModule.tick(mc);
```

### In the level-change reset block (where StashFinder.clear() etc. are called):

```java
StorageESP.clear();
OreSimModule.clear();
```

---

## 6. CorzModules.java — remove hollow stubs

Remove the hollow `AUTO_TOTEM` and `ANTI_TRAP` no-op code blocks; the
new modules handle those. Keep `Totem Counter` and `Target ESP`.

---

## 7. SettingsScreen.java — add setting rows

Follow the same pattern as the existing delay rows. For each module, add:
- A toggle row (boolean)
- Min/max delay rows (long) where applicable
- Mode rows (boolean sub-options)

Example for TriggerBot:
```java
// In the section for COMBAT modules:
rows.add(new SettingRow("Trigger Bot — attack players", t -> t.triggerBotAttackPlayers,  (t,v) -> t.triggerBotAttackPlayers = v));
rows.add(new SettingRow("Trigger Bot — cooldown gate",  t -> (int)(t.triggerBotCooldownThreshold*100), (t,v) -> t.triggerBotCooldownThreshold = v/100f));
rows.add(new SettingRow("Trigger Bot — delay min ms",   t -> t.triggerBotDelayMinMs,   (t,v) -> t.triggerBotDelayMinMs = v));
rows.add(new SettingRow("Trigger Bot — delay max ms",   t -> t.triggerBotDelayMaxMs,   (t,v) -> t.triggerBotDelayMaxMs = v));
```

---

## 8. Version bump

Change in `gtrace/build.gradle` and `fabric.mod.json`:
  `1.3.0` → `1.4.0`

---

## 9. Self-tests to add

Add a new test class `gtrace/selftest/src/main/java/dev/gtrace/OreSimTest.java`
that verifies `OreSimModule.predictChunk` returns positions in the valid
range for a known seed. Example:

```java
// For world seed 0 and chunk (0,0), all predicted Y must be in [7,119].
List results = predictChunk(0, 0, 0L); // expose via package-private
for (OreEntry e : results) {
    assert e.pos.getY() >= 7 && e.pos.getY() <= 119 : "out of range: " + e.pos.getY();
}
```

---

## 10. Known compile risks

1. `GizmoStyle.alwaysOnTopStroke(color, width)` — the exact method name is
   from the HANDOFF (`GizmoProperties.setAlwaysOnTop()` was the description).
   If that method does not exist, check `GizmoStyle` for an equivalent or
   use the Fabric API `SubmitRenderPhases.ALWAYS_ON_TOP` path instead.

2. `mc.level.blockEntityList()` — if this is not public in 26.3, use
   `mc.level.blockEntities` (the field) or iterate loaded chunks.

3. `InventoryMenu` slot numbers — verified from HANDOFF: InventoryMenu has
   46 slots total. Slot 45 = offhand. Slots 5–8 = armor. Slots 9–35 = main
   inventory rows. Slots 36–44 = hotbar. Always filter by
   `slot.container == player.getInventory()` if iterating generically.

4. `ArmorItem.getDefense()` and `ArmorItem.getToughness()` — these are the
   public accessors in 26.3 (from the HANDOFF's API notes).
