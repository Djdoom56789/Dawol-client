# PROMPT FOR SONNET — merge Corz + Goobaa + Krypton into DawolClient

Copy everything below the line into Sonnet.

---

You are extending **DawolClient**, a Fabric client mod for **Minecraft 26.3**, by merging the
feature sets of three other clients (Corz, Goobaa, Krypton) into it. The project already exists,
already builds, and already has 10 passing test suites. Your job is **not** to rewrite it.

## 0. ABSOLUTE RULES — violating any of these fails the task

1. **ZERO code injection.** No mixins, no class transformers, no access wideners, no ASM, no
   reflection into vanilla internals to change behaviour. This is the user's item 10 and is
   non-negotiable. If a feature *requires* injection, **do not build it** — write it in
   `TODO-BLOCKED.md` with the one-line reason.
   - This means: no reach manipulation, no velocity/fly/speed, no packet rewriting, no position
     desync, no camera position spoofing (freecam), no killaura that fakes rotation packets,
     no anti-knockback, no timer/tickshift, no scaffold, no noslow, no CritHack, no FakeLag.
2. **Do not copy code or binaries from Corz / Goobaa / Krypton.** They are closed-source, most
   are paid, and all use mixins you cannot use. Read their *public documentation and module
   lists*, observe *what the feature does*, and reimplement the behaviour in DawolClient's own
   idiom. Never decompile and paste.
3. **No blatant defaults.** Every new feature ships **off**. Passives may stay on (HUD, entity
   tracking of visible entities, config).
4. **Answers to the user: 5 sentences maximum.**
5. **Never claim a feature works if you could not verify it.** No GPU exists on this machine
   (AMD Radeon 840M crashes `0xC0000005` right after GL context creation), so *nothing* can be
   visually verified. Say so plainly.

## 1. Environment — exact versions, do not bump

| Item | Value |
|---|---|
| Minecraft | **26.3** (latest stable; 26.4 is snapshots only — do not target it) |
| Mapping | **None.** MC is unobfuscated since 26.1 — Mojang official names, no Yarn, no remap |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.162.0+26.3 |
| Loom | 1.15.5 |
| Java | 25 |
| Gradle | 9.4.0 |

Project root: `C:\Users\david\OneDrive\Documents\deepseek-harness\default-workspace\gtrace`
Build: `cd gtrace`, then `gradle build` (toolchain lives in `.tools/`; `source .tools/env.ps1`).
Artifact: `gtrace/build/libs/dawolclient-<ver>.jar`.
Version is declared in **two** files — `gtrace/build.gradle` (`version = '…'`) and
`src/main/resources/fabric.mod.json` (`"version"`). Bump both or they drift.

## 2. Current state (read these before touching anything)

`src/main/java/com/dawol/client/` — 35 files, 26 modules, 5 categories.

**Wiring:** `DawolClient.java` only. Hooks:
- `LevelRenderEvents.BEFORE_GIZMOS` → `EspRenderer`, `BlockEspRenderer`, `StashRenderer`,
  `CombatSystem.render`. This is the **only** safe gizmo submission point (`Gizmos` uses a
  ThreadLocal the engine installs for that phase; submitting elsewhere throws).
- `ClientChunkEvents.CHUNK_LOAD` → `StashFinder.scan`, `BlockEspRenderer.scanChunk`.
- `ClientTickEvents.END_CLIENT_TICK` → level-change reset, `Keybinds.poll`, combat, fullbright,
  player modules, Corz modules, `SchematicController.tick`.
- `HudElementRegistry.addLast` → `HudRenderer`.

**Files by role**
- Render: `EspRenderer`, `BlockEspRenderer`, `StashRenderer`, `HudRenderer`, `FullbrightModule`
- World: `StashFinder`, `OreClassifier`, `BlockSelection`, `BlockPickerScreen`
- Combat: `CombatSystem`
- Player: `PlayerModules`, `CorzModules`
- Schematic: `SchematicReader`, `SchematicLibrary`, `SchematicScreen`, `SchematicController`,
  `SchematicPlanner`/`SchematicBuilder`, `SchematicPlacer`, `SchematicExecutor`,
  `SchematicWalker`, `HumanizedBuilder`, `Humanizer`
- UI: `ModuleScreen`, `SettingsScreen`, `Theme`, `Module`, `Modules`
- Core: `DawolClient`, `Toggles`, `Config`, `Keybinds`, `Settings`, `Setting`, `DelayRandomizer`,
  `TracerAnchor`

**Tests:** `gtrace/selftest/src/main/java/dev/gtrace/` — 10 suites:
`GizmoProbeTest`, `StashFinderTest`, `RandomizerTest`, `BuilderHumanizerTest`,
`SchematicReaderTest`, `SchematicLitematicaTest`, `SchematicExecutorTest`,
`SchematicPlacerTest`, `SchematicWalkerTest`, `TracerAnchorTest`.
**All 10 must still pass when you are done.** Add a suite for every new pure-logic unit.

## 3. Frozen specifications — do not regress these

### 3.1 Tracers (was broken twice; has a test guarding it)
All tracers point at **one fixed point: the crosshair**.
- `TracerAnchor.of(mc.gameRenderer.mainCamera(), cameraEntity)` is called **once per frame** in
  `DawolClient`, and that single `Vec3` is passed into `EspRenderer`, `BlockEspRenderer` and
  `StashRenderer`.
- `ORIGIN_DISTANCE = 1.5D` — at 0.12 blocks the origin sat inside the near clip plane and the
  lines were clipped away. Do not reduce it.
- `tracersOnTop = true` — tracers must have their **own** through-wall flag, independent of box
  `espThroughWalls`, or every wall hides them.
- Up vector is `forward × right`. `right × forward` points down; that bug already happened once.
- Cap `maxTracers = 32`, applied **nearest-first**.
- **Rule: any renderer that draws a tracer takes the shared origin as a parameter. No renderer
  computes its own.** New tracers must obey this.

### 3.2 UI — Meteor layout, green palette, pixelated
Reference image: `.tools/refs/meteor-ui.png`. Green from `@GreenPvP.zip` (pack format 97).
- `ModuleScreen` layout: top tab strip `{Modules, Config, GUI, HUD, Friends, Profiles}`,
  **search box width 110**, category panels as named columns, bottom-right description box.
- Opened by **RSHIFT**. `L` opens the schematic browser. `G` toggles tracers.
- Palette lives only in `Theme.java` — 1px borders and flat fills **only**. **No gradients, no
  rounded corners, no textures.** Adding either is a regression.
- Greens: `GREEN 0xFF5DE07A`, `GREEN_DIM 0xFF388035`, `GREEN_DEEP 0xFF346D27`,
  `GREEN_BRIGHT 0xFF8CFFA8`. Category accents `CAT_COMBAT/RENDER/WORLD/PLAYER/MISC`.
- Drag panels, per-module settings screen, keybind list on the right.

**Any new category must be added to `Module.Category`, `Modules.register()`, the tab strip, and
`Theme`'s accent set — all four, or the module is invisible.**

### 3.3 Humanisation — every automated action
Nothing automated may run on a fixed interval; a constant period is a timing fingerprint.
- All delays go through `DelayRandomizer.sample(min, max)`, floor 50 ms, ceiling 10 000 ms.
- `Humanizer` supplies: `placementDelay()`, `occasionalPause()`, `aimErrorDegrees(distance)`
  (error scales with distance), `rollsMistake()` → `WRONG_BLOCK | MISSED_CLICK | OVERSHOOT`,
  then a **verification re-queue** so the mistake is actually recovered.
- Existing knobs in `Toggles` you must keep honouring: `autoDelayMin/MaxMs`,
  `reachJitterMin/MaxMm`, `rotationJitterMin/MaxMilliDeg`, `buildDelayMin/MaxMs`,
  `buildPauseChancePercent`, `buildPauseMin/MaxMs`, `buildMistakeChancePercent`,
  `buildDoubleCheckChancePercent`.
- Every new auto-module gets its own min/max window in `Toggles` + `Config` + `SettingsScreen`.
  A hardcoded `Thread.sleep(500)`-style constant is a **failing** submission.

## 4. What to take from each client

Research each one's public module list first (do not guess); then map into the table.
Seeds already established:

**Corz** — ~115 modules advertised (24 combat, 68 visuals/movement, 23 DonutSMP); `.litematic`
`.schem` `.nbt` schematic builder. Already partly merged into `CorzModules` (Auto Totem, Totem
Counter, Target ESP). Candidates still unbuilt: AutoTool, StorageESP, HoleESP, TriggerBot,
AntiTrap variants, AutoAnchor, Elytra/Mace helpers, ChestStealer, FastPlace, FastBreak,
Velocity(no), KillAura(no), etc.

**Goobaa** — crystal-PvP / anarchy oriented; expect AutoCrystal, Surround, HoleFill, AutoArmor,
AutoTotem, AnchorAura, BedAura, Burrow-class features, HUD suite.

**Krypton** — DonutSMP targeted. Key contributions:
- **Base finder that *mines* to a target** instead of reading chunks — `/rtp` then mine straight
  down, or tunnel in a straight line underground to defeat anti-ESP. *The movement half is
  already in `SchematicWalker`; extend it, do not duplicate it.*
- **Anti-Trap** (armour stands made vanishing) — currently in `Toggles` as `antiTrap` but the
  module is **hollow**; implement it.
- **Xray-Netherite**, based on OreSim: *simulate* ore spawn from the chunk seed and only mark
  what would actually generate. This is legitimate client-side maths and is the single most
  valuable thing to take from Krypton.

### Classification — apply to every candidate module

| Verdict | Examples |
|---|---|
| **BUILD** (pure client-side read/render) | StorageESP, HoleESP, OreSim/Xray, AutoTool, ChestStealer (vanilla container-click path), FastPlace (gated by vanilla cooldowns, humanised), AutoArmor, AutoTotem, Surround/HoleFill/AutoAnchor via normal right-click, TriggerBot gated by reach+LOS+cooldown+humanised aim delay, BetterChat, Nametags, HUD suite, AntiTrap, tracers for containers |
| **BUILD, degraded** | Fullbright — `Options.gamma()` is `OptionInstance<Double>` built on `UnitDouble`, which **silently rejects anything outside [0,1]**, so 1.0 is a hard ceiling. Say so in the javadoc. Real lightmap brightening needs a mixin → forbidden. |
| **BLOCKED — write to `TODO-BLOCKED.md`, do not implement** | Freecam, Velocity, Fly, Speed, Timer, NoSlow, Reach, KillAura with packet rotation, Scaffold, FakeLag, AntiKB, Xray that patches chunk rendering, anything needing a camera position other than the camera entity |

Freecam note so it is not re-attempted: in 26.3 `Camera.position()` **is** the camera entity's
position, and the only public controls are `getCameraEntity()`/`setCameraEntity()`. Moving the
real player is server-detectable; a separate camera entity is equally visible. It is blocked.

## 5. Verified 26.3 API facts — build against these, do not re-derive

- `Minecraft` is `net.minecraft.client.Minecraft`; `Component` is `net.minecraft.network.chat.Component`;
  `Identifier` is `net.minecraft.resources.Identifier` (`.fromNamespaceAndPath`).
- `Minecraft.setScreenAndShow(...)` — `setScreen` is gone; `Minecraft.screen` is **private**.
  To detect "a screen is open", read the mouse-grab state instead.
- `Minecraft.getCameraEntity()` is `@Nullable` — **always null-check it**; NPE inside the render
  hook already happened once.
- `LocalPlayer.respawn()` is the only respawn trigger; `DeathScreen.shouldCloseOnEsc()` is false.
- `InventoryMenu` has **46** slots, `Inventory.getContainerSize()` is **41**. Never bound a menu
  loop by inventory size — filter `slot.container == player.getInventory()`.
- `MultiPlayerGameMode.handleContainerInput(...)` is public; `ContainerInput.SWAP` + offhand
  slot **40** = the F-key swap. Use this for AutoArmor/AutoTotem/offhand work.
- `Gizmos.line/cuboid/rect/billboardText`; `GizmoProperties.setAlwaysOnTop()` = through-wall.
- `Block.getStateForPlacement` is **public** — predict placement state by asking the block, never
  from a hand-written per-block table (that table drifted once already).
- `NbtIo.readCompressed` **already gunzips** — wrapping in `GZIPInputStream` corrupted a load.
- Litematica `BlockStates` is `long[]`, not `byte[]`. `ChunkPos.asLong` does not exist — key
  chunks as `((long)x << 32) | (z & 0xFFFFFFFFL)`.
- `CompoundTag.keySet()` (not `getAllKeys()`); `ListTag.getCompound(int)` returns `Optional`.
- `ItemStack` **cannot** be constructed headlessly (components unbound) — use the
  `HumanizedBuilder.HotbarLookup` interface in any logic you want to test.
- Headless registries need `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()`.

## 6. Workflow

1. Read `MEMORY.md` in this folder — it is the source of truth for decisions already made.
2. Read the current `Toggles`, `Modules`, `Theme`, `Config`, `DawolClient` before editing.
3. One module at a time, end to end: `Toggles` flag → logic class → `Modules.register()` entry →
   `Config` save+load key → `SettingsScreen` rows → tick/render hook in `DawolClient` → selftest.
4. **Config key sets must stay symmetric** — 11 flags silently reverted to default in 1.1.0
   because save and load diverged. Diff them mechanically at the end.
5. `Remove-Item build` (recursively) before building — OneDrive locks it.
6. Full `gradle build` must pass all suites before you report anything as done.

## 7. Deliverables

- Source changes in `gtrace/src/main/java/com/dawol/client/`.
- New selftest suites for every new pure-logic unit.
- Updated `.jar` copied to `C:\Users\david\OneDrive\Desktop\Minecraft\`.
- `MEMORY.md` updated in place (rewrite, do not append — it must stay short).
- `TODO-BLOCKED.md` listing every feature refused under rule 1 and why.
- A final report ≤5 sentences: what ships, what was refused, what is unverified.

## 8. Failure modes that have already burned this project — do not repeat

- PowerShell `Set-Content -Encoding utf8` writes a **BOM**; `javac` then fails with
  `illegal character: '\ufeff'`. Write files with `New-Object System.Text.UTF8Encoding($false)`.
- `Set.of(...)` with a duplicate element throws `ExceptionInInitializerError` at class init.
- Compile-clean ≠ working. Every bug below was caught by a test, not by the compiler: dead
  anti-kick code, NBT double-decompress, litematica byte/long, stash scoring letting a huge
  empty build qualify, tracer up-vector sign, duplicated `Set.of` entry.
- Re-scanning used to **append** duplicates instead of replacing — a scan is a census, reset first.
- Sorting the whole backing map every frame per frame is an allocation fire; cache with a dirty flag.
