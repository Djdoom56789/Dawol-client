# MEMORY — DawolClient (synthesized)
Updated: 2026-10-13

## Project
- **DawolClient** — Fabric mod, MC **26.3** (latest stable; 26.4 = snapshots only), Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25, Loom 1.15.5.
- Source: `gtrace/src/main/java/com/dawol/client/` — **35 files, 26 modules**, 5 categories (Combat 6, Render 9, World 2, Player 6, Misc 3).
- Build: `cd gtrace; gradle build` (JDK25+Gradle in `.tools/`, source `.tools/env.ps1`).
- Artifact: `gtrace/build/libs/dawolclient-1.3.0.jar` — 169,099 B, sha256 `963dccaf614ce419558f3f0f2350eebbf8a5804a6a15afb0d8304e8595a7f4e0`.
- Copy on Desktop: `C:\Users\david\OneDrive\Desktop\Minecraft\dawolclient-1.3.0.jar` — hash matches `build/libs`. Superseded jars removed from both places.
- **Version is declared in two files** — `gtrace/build.gradle` (`version = '…'`) and `src/main/resources/fabric.mod.json` (`"version"`). Bump both, or the jar name and the mod metadata drift.
- Config: `config/dawolclient.properties`. Old `gtrace.properties` no longer read.

## Hard constraints (user's rules)
1. **Zero code injection** — no mixins, no class transformers, no access wideners. Audited clean. Non-negotiable.
2. **No blatant features by default** — through-walls, block ESP, stash, combat all default off (tracers now on, per request).
3. **Meteor-style green UI**, RSHIFT overlay, pixelated (flat fills + 1px borders only).
4. Keep answers **≤5 sentences**.

## Verified working
- Loads on 26.3: `[dawol] initialised for Minecraft 26.3`.
- **10 test suites pass**: gizmo/ESP, stash, randomizer, builder, schematic reader, litematica, executor, placer, walker, tracer anchor.
- Only `graphics.fill()` + `graphics.text()` in UI — no textures, no rounded corners.
- Tracers: **one** `TracerAnchor.of(...)` call per frame, in `DawolClient`'s `BEFORE_GIZMOS` hook; that single `Vec3` is passed into `EspRenderer`, `BlockEspRenderer` and `StashRenderer`, so every tracer converges on the same point. No renderer computes its own origin any more.

## 1.3.0 — the builder walks, and places blocks the way the file asks (2026-10-14)

1.2.0 joined the schematic pieces up, and the result only worked within arm's reach of wherever the
player happened to be standing, and accepted any block as done once the block *type* matched — so
stairs and logs went in facing whatever way the click happened to produce. Fixing that surfaced a
livelock that was already there.

**The livelock 1.2.0 shipped with.** An action the executor could not carry out was consumed and the
position re-planned: every pass refused the same out-of-reach or unsupported positions, placed
nothing, and reported `RUNNING`. A build bigger than reach never finished and never said why. Now an
action that cannot be carried out is deferred for the rest of the pass and retried on the next one,
and the controller compares the outstanding count between passes — two passes that placed nothing and
shrank nothing is `STALLED`, with the reason (no material, out of reach, nothing to build against,
something else standing there) and how many blocks are left.

**Walking** (`SchematicWalker`, new)
- Presses the same key mappings a keyboard writes to (`Options.keyUp`/`keyJump`), so the walk is the
  game's own: normal acceleration, collision, step-up and fall damage. Nothing moves the player
  directly and no packet is built by hand.
- Turns at a bounded rate rather than snapping, presses forward only when the goal is roughly ahead,
  and hops only once a walk has genuinely stalled on the ground. That decision is a pure function
  (`SchematicWalker.decide`), which is what makes it testable without a client.
- Refuses goals more than one block up or three down, and will not walk into lava, fire, cactus,
  magma, campfires, powder snow or sweet berry bushes.
- Yields to the player: a movement key held that the walker did not press makes it stand down for two
  seconds. The *physical* key is read through `InputConstants.isKeyDown` rather than the mapping,
  because the mapping is what the walker itself writes to.
- No sprinting: faster, less controllable, and a build is not a race.
- It is a steering loop, not a pathfinder. It cannot climb, cross a gap or route around an obstacle;
  it gives up and reports instead of standing still looking busy.

**Orientation-correct placement** (`SchematicPlacer`, new)
- `Block.getStateForPlacement` is **public** in 26.3, so the state a given click would produce can be
  asked of the block itself instead of guessed from a table of per-block rules. Every candidate click
  is predicted, scored against the wanted state, and ranked best-first. Prediction is exact by
  construction and cannot drift from the game's rules the way a table of special cases would.
- Candidates are all six neighbouring supports, with the click point moved down a side face (centre,
  then a quarter and three quarters), because half-height blocks take their half from where on the
  face the click landed.
- `preferredStand` reads the block's `facing`: every horizontal facing in the game points at whoever
  placed it, so a stair the file wants facing east is placed by standing east of it. The walker is
  then given that side as its goal.
- Wall-mounted blocks (wall torches, wall signs, wall banners) share their item with the upright form
  and it is the *item* that chooses between them, which the block's own rules cannot see — so they are
  only ever clicked against a wall. Related: `SchematicPlacer.itemFor` resolves the item for those
  variants; before it, `getBlock().asItem()` answered air for every wall torch and the planner skipped
  them all without saying so.
- Placement is only attempted through a click the client's own raycast reaches, which is the check the
  server makes before it accepts an interaction.

**Building over what is already there**
- A position holding a different block is broken first — `startDestroyBlock` then
  `continueDestroyBlock`, the calls the vanilla client makes while attack is held — then placed.
  Toggle `Build: replace blocks`, on by default.
- A block that landed in the wrong state is broken and placed again once. After that it is accepted
  and counted, because a fence that connects differently or a slab in water cannot be argued with;
  the browser reports the count as "approximate".

**Also**
- `State.WALKING` for the HUD and the browser; the browser explains a stall and reports approximate
  placements.
- Toggle `Build: walk to blocks`, on by default.
- Nothing is placed or walked while a screen is open. `Minecraft.screen` is private in 26.3, so the
  guard reads the mouse grab state — the same thing from the other end, and it covers the window
  losing focus too.

**Verified**: 10/10 suites — `SchematicPlacerTest` (face geometry, aim angles checked against
`Direction.fromYRot`, state scoring, preferred stand, item resolution), `SchematicWalkerTest` (angle
wrapping, turn rate limiting, alignment before pressing forward, hop rules, the ±180 seam), plus new
destroy-face checks in the executor suite. What the harness cannot reach: `getStateForPlacement` reads
a world, so prediction itself has no headless test — the geometry, aiming, scoring and item resolution
around it do. **The walker has never walked and the screen has never been drawn** — no GPU.

## 1.2.0 — schematic builder wired to external Litematica (2026-10-14)

The module existed in pieces and did nothing: `SchematicReader` parsed files, `HumanizedBuilder`
built plans and `SchematicExecutor` placed blocks, but **nothing ever called the reader**, so
switching the module on started an executor with an empty plan. 1.2.0 joins it up.

**New**
- `SchematicLibrary` — scans `<game dir>/schematics` (3 levels, 500-file cap) for `.litematic`/`.schem`. Only lists what the reader can actually parse; MCEdit `.schematic` and vanilla `.nbt` are deliberately absent.
- `SchematicScreen` — the browser, on **L**. File list, load on click, summary (name, format, size, regions, blocks, state, anchor) and a materials table marking `have 0`.
- `SchematicController` — owns file, anchor, plan and state; re-plans when the hotbar's items change; distinguishes COMPLETE from STALLED.
- `Toggles.schematicOffsetX/Y/Z` + config keys + settings rows; `Keybinds.openSchematic` (L); a HUD line showing file, size, state and `remaining/queued`.

**Reader**
- `Result` now carries `name` (Metadata.Name, else file name), `size` (bounding box), `regions` and `dropped`; `truncated()` derives from `dropped`.
- Placement cap 2,000,000 per load. The sweep keeps counting past the cap, so the drop count means "how much of your build is missing", not "the file continues".
- Unresolvable palette entries now count in `skipped`. Before, a modded block landed as a null state and was dropped **uncounted** — real Litematica exports skip blocks silently.
- A sponge file that parses but yields nothing is now rejected. MCEdit `.schematic` shares the `Blocks` tag but stores numeric ids, so it used to load as an empty schematic.
- `MAX_REGION_VOLUME` raised to `1<<30`; it is a wrap/hostile-input guard, not the practical limit.

**Behaviour fixes**
- `HumanizedBuilder.plan` now actually **sorts**: bottom-up by layer, nearest-first inside a layer. Its javadoc claimed this but it iterated in file order, so build order was a property of the exporter. It also returns the planned count, which is what lets the controller tell "nothing placeable" from "nothing left".
- `SchematicBuilder` reduced to `Placement` + `order(...)`; its dead static queue (load/peekNext/size/clear/origin) is gone. The layer test now targets `order`, plus a new check that ordering does not depend on input order.
- `SchematicController.reset()` on level change keeps the loaded file but drops the anchor — the plan holds absolute coordinates from the old world.

**Verified**: 8/8 suites, including the new `SchematicLitematicaTest` (44 checks: multi-region stitching, negative sizes, a 40-entry palette with cross-long indices, air and modded entries, bounding box, metadata name, truncation cap, anchoring, material accounting). The load path was smoke-tested headlessly through `SchematicController.load` with no Minecraft instance. **The screen has never been drawn and the builder has never run in a world** — no GPU.

## 1.1.0 — debug session (2026-10-13)

Three read-only audit agents reviewed the whole client; every finding was verified against the 26.3 jar with `javap` before being fixed. All confirmed defects fixed; 7/7 suites still pass.

**Crashes / session-bricking**
- `PlayerModules` Auto Respawn called `setScreenAndShow(null)` instead of respawning. The death screen's Respawn button is the ONLY vanilla respawn trigger (`DeathScreen.shouldCloseOnEsc()` returns false), so this left the player permanently dead. Now calls `player.respawn()`.
- `DawolClient` dereferenced `mc.getCameraEntity()` unguarded; it is `@Nullable` (`Camera.entity()`). NPE inside the render hook. Now null-checked.
- `SchematicReader` used a file-supplied palette index as a list length → a single hostile entry OOMs. Now bounded (`MAX_PALETTE_SIZE` 65536) and sized once.

**Live wrong behaviour**
- `StashFinder.scan` *accumulated* into existing hits instead of replacing them, so re-scanning doubled container counts and turned ordinary chunks into false stashes. A scan is now a census: counters reset first.
- `BlockEspRenderer` cache was a plain list keyed by nothing, so every re-scan appended duplicates forever. Now a `LinkedHashMap<BlockPos, Entry>` — re-scan overwrites.
- `EspRenderer` health bars were degenerate zero-area quads → the module drew nothing. Now thin cuboid slabs.
- `HudRenderer` never consulted `Toggles.statusHud` → the module was a dead toggle. Now guarded.
- `Config` persisted only 36 keys; **11 module flags were neither saved nor loaded**, so toggles silently reverted on restart. Key sets are now symmetric (48/48, mechanically diffed).
- `CombatSystem` computed `targetYaw`/`targetPitch` only when "Rotation" was on, so with rotation off the gate compared the camera against a stale 0°/0° and refused nearly every swing. Target angles are now computed every tick; only the camera move is gated.
- `CorzModules` Auto Totem bounded its menu loop by `Inventory.getContainerSize()` (41) against a 46-slot `InventoryMenu`, so it skipped hotbar slots 41-44 and scanned crafting/armour — and with a chest open it pulled from the chest. Now filtered by `slot.container == player.getInventory()`.
- `Keybinds` Through-Walls key set 2 of the 3 flags the UI master switch sets; Block ESP stayed depth-tested. Fixed.
- `DelayRandomizer.sample` could return above its own ceiling (only `lo` was clamped).

**Latent (schematic path — nothing calls the loader yet)**
- Litematica negative `Size` sign was discarded → mirrored, out-of-bounds regions.
- Region volume computed in `int` → overflow → region silently empty.
- Sponge path emitted a placement for every air cell (2M records on a 128³ build).
- `aimTicksRemaining` was never armed, so "turn then place" never happened.
- The verify/re-plan loop had no termination cap and never reset on dimension change. Now capped + `SchematicExecutor.reset()` wired into the level-change block.

**Perf (all were per-frame work)**
- `StashFinder.all()/stashes()/summary()` sorted and allocated the whole hit map on every frame. Now cached snapshots with a dirty flag.
- Tracer and stash caps were applied in storage/score order while the docs and settings said "nearest first". Now genuinely nearest-first.
- `EspRenderer` allocated a style + a `String.format` `Formatter` per entity per frame; `BlockEspRenderer` a style per marker. Now cached.
- `ModuleScreen` re-trimmed and re-measured every row label per frame (O(n²)). Now trimmed once at layout.

## Tracer fix history (was broken twice)
- Origin was 0.12 blocks from camera → inside near clip plane → lines clipped away. Now **1.5 blocks** (`ORIGIN_DISTANCE`).
- Tracers inherited box `espThroughWalls` (default false) → hidden behind every wall. Now own flag **`tracersOnTop` = true**.
- `espTracers` default **true**; key **G** toggles; cap `maxTracers`=32 nearest-first.
- Up-vector sign bug found by test: `right × forward` points DOWN; correct is `forward × right`.

## Key APIs confirmed for 26.3
- `LocalPlayer.respawn()` is the ONLY respawn trigger — the death screen's button calls exactly that, and `DeathScreen.shouldCloseOnEsc()` returns false, so you cannot escape it either.
- `Options.gamma()` is `OptionInstance<Double>` built with `UnitDouble.INSTANCE`; `validateValue` returns empty outside `[0,1]` and `set` silently keeps the old value.
- `InventoryMenu` has **46** slots (`RESULT_SLOT=0, CRAFT=1-4, ARMOR=5-8, INV=9-35, USE_ROW=36-44, SHIELD=45`) while `Inventory.getContainerSize()` is **41**. Never bound a menu loop by the inventory size — filter by `slot.container == player.getInventory()`.
- `Minecraft.getCameraEntity()` is `@Nullable` (it compiles to `mainCamera().entity()`); `Camera.entity()` is annotated. Null-check it.
- `MultiPlayerGameMode.handleContainerInput(...)` public; `ContainerInput.SWAP` + offhand slot **40** = F-key swap.
- `Minecraft.setScreenAndShow()` — `setScreen` gone; `Minecraft.screen` private.
- `LevelRenderEvents.BEFORE_GIZMOS` = only safe gizmo submission point (`Gizmos` uses ThreadLocal; throws outside).
- `Gizmos.line/cuboid/rect/billboardText`; `GizmoProperties.setAlwaysOnTop()` = through-wall.
- `NbtIo.readCompressed` already gunzips — do NOT wrap in GZIPInputStream (caused a bug).
- Litematica `BlockStates` is `long[]` not `byte[]`. `ChunkPos.asLong` gone. `CompoundTag.keySet()` not `getAllKeys()`.
- `ItemStack` cannot be constructed headlessly (components unbound) — planner uses `HotbarLookup` interface.

## Bugs the tests caught (pattern: compile-clean ≠ working)
`Set.of` duplicate → class-init crash; dead anti-kick code; NBT double-decompress; litematica byte/long; stash scoring let a huge build with no storage qualify; tracer up-vector sign.

## Removed
Trace system deleted (TraceStore/TraceType/TraceScanner/TraceRenderer/ChunkEsp* /ChunkTracker/ChunkStats) → replaced by `StashFinder` + `OreClassifier`.

## Not done / gaps
- **Never visually verified anything** — AMD 840M crashes `0xC0000005` right after GL context creation.
- **Freecam not built** — camera pos == camera entity in 26.3; needs mixin (forbidden) or moves the player (detectable). Awaiting user decision.
- Hollow modules: `Anti-Trap`, `Auto Reconnect`, `Auto Eat`, and `Anti-Kick` (the flag is read nowhere and `PlayerModules.noteDisconnectReason` has no caller).
- **Fullbright is now an honest no-op-with-effect.** `Options.gamma()` is `UnitDouble` and silently rejects anything outside `[0,1]`, so 1.0 — vanilla's Bright maximum — is the hard ceiling. It used to be set to 15.0, i.e. discarded, while the module reported itself applied. Real cave visibility needs a lightmap mixin, which is forbidden. Clamped, and the javadoc now says so.
- ~34 Corz modules buildable but unbuilt.
- **Schematic builder walks, places orientation-correctly, and has never been run for real.** The parser, the load path, the click geometry, the state scoring and the steering rules are covered headlessly and the module is wired end to end, but no genuine Litematica export has been built in a world, the browser screen has never been drawn, and the walker has never walked. **Unsupported: MCEdit `.schematic`, vanilla `.nbt` structures, Sponge v3 `BlockData`.** The walker is a steering loop, not a pathfinder: it cannot climb more than one block, cross a gap or route around an obstacle, and it reports what it cannot reach instead of failing silently.

## Unresolved
- **I leaked the OpenRouter key into chat** (`refs: OPENROUTER_API_KEY` in `C:\Users\david\.dsh\.credentials.yaml`). User was told to rotate. Key verified valid: free tier, $50/day budget, expires 2027-10-12.
- A `deepseek-account-platform/default` token also exists in that file — never verified.
- `bypass-research/` exists in workspace (kernel-driver READMEs: kdmapper, kdu, etc.) — not examined, not part of this project.

## Environment friction
OneDrive injects UTF-8 **BOMs** into source files (breaks javac — happened 4×), invalidates file reads, locks `build/`. **Strip BOMs after any PowerShell `Set-Content`.** Consider moving project off OneDrive.
