# HANDOFF — DawolClient

- **Date:** 2026-10-14
- **Session:** full transcript of both sessions in this folder: `CHAT-SESSION.md`
- **Project root:** `C:\Users\david\OneDrive\Documents\deepseek-harness\default-workspace\gtrace\`
- **Source:** `gtrace\src\main\java\com\dawol\client\`
- **Version at handoff:** **1.3.0**

---

## What this is

DawolClient — a client-side Fabric mod. Minecraft **26.3** (unobfuscated), Java 25, Fabric Loader
0.19.5, Fabric API 0.162.0+26.3, Loom 1.15.5. Modules across 5 categories (Combat, Render, World,
Player, Misc) plus UI screens.

**Non-negotiables — do not break these:**

1. **Zero code injection.** No mixins, no class transformers, no access wideners. Re-audited clean at 1.3.0: no mixin/refmap/transformer entries in the jar.
2. **No packet falsification**, no hand-built packets. Vanilla entry points only — including the movement, which drives the game's own key mappings rather than setting positions.
3. **UI:** flat fills + 1px borders only. No textures, no rounded corners. Meteor-style green.
4. **All tracers converge on one point.**

Author `Dawol`, mod id `dawolclient`, log tag `[dawol]`, config `config/dawolclient.properties`.

---

## State at handoff

- Shipped jar: `dawolclient-1.3.0.jar`, **169,099 B**, sha256 `963dccaf614ce419558f3f0f2350eebbf8a5804a6a15afb0d8304e8595a7f4e0` — in this folder and in `gtrace/build/libs/`.
- Build: **BUILD SUCCESSFUL**, **10/10 self-test suites pass**. Superseded jars removed from this folder.
- Jar metadata verified: `fabric.mod.json` reports `"version": "1.3.0"`, manifest `Implementation-Version: 1.3.0`. 81 entries.
- New at 1.3.0: `SchematicPlacer`, `SchematicWalker`; `SchematicExecutor` rewritten around a travel/aim/click/settle/clear state machine; `SchematicController` gained the `WALKING` state, a stall reason and a progress guard; `HumanizedBuilder` gained item resolution for wall-mounted variants and a verification filter.

---

## The schematic subsystem

Read this before touching anything schematic. It is the largest single feature in the mod and the
only one whose correctness depends on the build being carried out over time.

**The chain:** `SchematicLibrary` (finds files) → `SchematicScreen` (picks one, **L** key) →
`SchematicController` (owns file, anchor, plan, state) → `HumanizedBuilder` (queues humanised
actions) → `SchematicExecutor` (performs them) → `HudRenderer` (reports the state).

```
SchematicLibrary ─► SchematicScreen ─┐
                                     ▼
                        SchematicController ──► HumanizedBuilder
                                     │                    │
                                     │                    ▼
                                     │            SchematicPlacer ── what click would work
                                     ▼                    │
                              SchematicWalker ─────────► SchematicExecutor ──► MultiPlayerGameMode
                              (key mappings)                                  (useItemOn / destroy)
```

**Files:** `SchematicLibrary.java` · `SchematicScreen.java` · `SchematicController.java` ·
`SchematicReader.java` · `SchematicBuilder.java` · `HumanizedBuilder.java` · `Humanizer.java` ·
`SchematicPlacer.java` · `SchematicWalker.java` · `SchematicExecutor.java`. Schematic files live in
`<game dir>/schematics/`.

**The invariants that matter:**

- The plan is only ever valid for the hotbar it was built from, because `HumanizedBuilder.plan` skips
  any placement whose item is not carried. `SchematicController` re-plans when the hotbar's contents
  change (compared item by item each tick) and when the executor stops.
- **`placed()` compares block types, not states.** That is deliberate: chasing exact states would make
  verification loop forever on a stair that is merely rotated. State correctness is handled at
  placement time by `SchematicPlacer`, and once — with a break-and-replace — after the fact.
- **The plan is absolute.** The anchor is a pure translation applied in `SchematicController.anchored`.
  A level change calls `SchematicController.reset()`, which drops the anchor and the executor's plan
  but keeps the file loaded.
- **The executor holds the action it is working on** (`current`) until it is accepted; `builder.next()`
  moves the cursor only at the end. The action stays the same across the travel, aim, click and settle
  phases, and `builder.peek()` returning a different instance is what tells it a re-plan happened.

**States:** NONE / IDLE / RUNNING / WALKING / COMPLETE / STALLED. `STALLED` carries a reason string
("12 left, out of reach"). `STALLED` is computed from the *file* re-checked against the world, plus
the pass-over-pass progress guard — not from the executor's queue, which only ever held the part of
the build the hotbar could service and reach.

**Deferral, and why the progress guard exists.** An action that cannot be carried out is added to
`deferred` for the rest of the pass, so the rest of the build proceeds and the position is retried on
the next pass — which matters because a block whose neighbour does not exist yet becomes placeable
once that neighbour lands. Left at that, it would loop forever: 1.2.0 shipped exactly that bug, and
the symptom was a build reporting RUNNING while placing nothing. `SchematicController` therefore
remembers the outstanding count and the number of actions the last pass placed, and gives up after
two passes that placed nothing and shrank nothing. **Do not remove that guard without replacing it
with something that terminates.**

**Placement states, and how they are got right.** `Block.getStateForPlacement` is **public** in 26.3,
so `SchematicPlacer.rank` asks each candidate click what it would produce and scores the answer
against the file. The player's rotation is set to the aim the click implies and restored around each
prediction, because the prediction reads the player's facing. Wall-mounted variants (wall torch, wall
sign, wall banner) are special-cased twice: their item is resolved by name in `itemFor`, and they are
only offered a wall to click, because the *item* — not the block's rules — is what chooses between the
standing and wall forms.

**Movement.** `SchematicWalker` presses `Options.keyUp`/`keyJump`, so the walk is the game's own
physics. It turns at a bounded rate, presses forward only when roughly aligned, hops only when a walk
has actually stalled on the ground, refuses goals more than one block up or three down, refuses to
walk into hazards, and stands down for two seconds when it sees a movement key held that it did not
press (read through `InputConstants.isKeyDown`, since the mapping is what it writes to). It is a
steering loop, not a pathfinder — it cannot climb, cross a gap or route around an obstacle. It gives
up and reports rather than grinding.

**Caps and honesty:** `SchematicReader.MAX_PLACEMENTS` is 2,000,000 per load; overflow is counted
(`Result.dropped`), as are palette entries naming a block this client does not have (`Result.skipped`).
A sponge file that parses but yields nothing is rejected — that is the MCEdit `.schematic` case, which
shares the `Blocks` tag but stores numeric ids. Positions accepted with the wrong state are counted and
reported in the browser as "approximate".

**What has and has not been verified:** parser (`SchematicLitematicaTest`, `SchematicReaderTest`), load
path (smoke-tested headlessly through `SchematicController.load`), click geometry, aim angles, state
scoring and item resolution (`SchematicPlacerTest`), steering rules (`SchematicWalkerTest`),
placement/destroy geometry (`SchematicExecutorTest`). **What is not verified: the prediction itself
(`getStateForPlacement` reads a world, so it has no headless test), the walk, and every pixel.** See
the GPU note below.

---

## Build recipe

```powershell
. 'C:\Users\david\OneDrive\Documents\deepseek-harness\default-workspace\.tools\env.ps1'
cd 'C:\Users\david\OneDrive\Documents\deepseek-harness\default-workspace\gtrace'
gradle build --offline --console=plain
```

Jar lands in `gtrace\build\libs\dawolclient-<version>.jar`. The ten suites
(`:selftest:build` → runStashTest, runGizmoProbe, runRandomizerTest, runBuilderTest,
runSchematicTest, runLitematicaTest, runExecutorTest, runPlacerTest, runWalkerTest, runTracerTest) run
as part of `build`.

**To cut a new version**, the number lives in **two** files and both must change or the jar name and
the mod metadata drift:

- `gtrace/build.gradle` → `version = '1.3.0'`
- `gtrace/src/main/resources/fabric.mod.json` → `"version": "1.3.0"`

Then rebuild and copy the jar into this folder.

---

## Environment gotchas

**OneDrive is hostile to this build.** Both the source and this folder live under
`C:\Users\david\OneDrive\`. It locks `gtrace\build`, and it shows up three ways:

```
Execution failed for task ':compileJava'.
> java.io.IOException: Unable to delete directory ...\gtrace\build\classes\java\main
```
```
Execution failed for task ':selftest:compileJava'.
> Failed to delete some children. This might happen because a process has files open
```
```
Execution failed for task ':compileJava'.
> Failed to clean up stale outputs
```
All three mean the same thing. **Recovery:** `gradle --stop`, delete `build\` and `selftest\build`,
then rebuild. **Never run two gradle builds on this project at once** — the second fails on the same
lock. It also injects UTF-8 BOMs into source files (breaks `javac`); strip them after any PowerShell
`Set-Content`. Moving `gtrace` off OneDrive is still the right fix.

**Never pipe gradle's live output through `Select-String`/`Select-Object`.** The pipeline buffers and
the command reports a timeout with no output describing a build that in fact succeeded. Redirect to a
file and read the file:

```powershell
gradle build --offline --console=plain *> "$env:TEMP\out.log"; "EXIT=$LASTEXITCODE"
```

Note that `*>` writes **UTF-16LE**, so `grep` sees binary — convert with
`iconv -f UTF-16LE -t UTF-8` or read it from PowerShell with `-Encoding Unicode`.

**The client cannot be visually verified on this machine.** The AMD Radeon 840M dies with
`0xC0000005` immediately after GL context creation — `gtrace\run\logs\latest.log` ends at texture atlas
creation. Geometry, plumbing, NBT parsing, planner output and steering arithmetic are all testable
headlessly; **pixels are not**, and neither is anything that needs a live world. Never claim visual or
in-world verification.

**Useful for API checks:** `javap` at
`C:\Users\david\OneDrive\Documents\deepseek-harness\default-workspace\.tools\jdk-25.0.4.1+1\bin\javap.exe`,
against the real game jar at
`C:\Users\david\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar`.
Note: `javap.exe` invoked directly from `bash` fails with exit 127 in this environment; it works via
`powershell`, and via `Bun.spawnSync` in `eval(js)`.

**26.3 API notes gathered across these sessions** (each of these cost a compile or a probe):

- `Block.getStateForPlacement(BlockPlaceContext)` is **public**. `BlockItem.getPlacementState` is
  protected; `BlockItem.place` is public but mutates the world.
- `BlockPlaceContext(Player, InteractionHand, ItemStack, BlockHitResult)` is public, and derives the
  placement position from the hit result.
- **`Minecraft.screen` is private** — use `mouseHandler.isMouseGrabbed()` to tell whether a screen is
  up. `Minecraft.setScreenAndShow(Screen)` is how screens are opened; there is no `getScreen()`.
- `Window.handle()` is the accessor, not `getWindow()`.
- `ClientInput` (`net.minecraft.client.player.ClientInput`) is the input class; its `keyPresses` field
  is rebuilt from the key mappings each tick, so driving `KeyMapping.setDown` is the reliable lever.
  `KeyMapping.getDefaultKey()` gives the default binding, not the current one.
- `InputConstants.isKeyDown(int)` takes only the key value now, and is a physical keyboard query.
- `MultiPlayerGameMode.destroyBlock` does nothing outside creative; the survival path is
  `startDestroyBlock` then `continueDestroyBlock` repeated across ticks.
- `Block.getStateForPlacement` returning null means "cannot be placed by this click" — useful, not a
  failure.
- Banners (`Blocks.WHITE_WALL_BANNER` / `Items.WHITE_BANNER`) no longer exist in 26.3.
- `Entity.horizontalCollision` and `Entity.onGround()` are the signals to use for "the walk is
  blocked"; no collision-shape query is needed.
- `Item` has **no** no-arg `getName()`; use `Component.translatable(item.getDescriptionId())`.

---

## Open items

- **Freecam — still blocked on a user decision.** True detached freecam needs a mixin (forbidden).
  Alternatives: passive zoom-out camera (undetectable, not real freecam) or moving the camera entity
  (server-visible). See `BRIEFING.md` §4.
- **Fullbright is now honest but limited.** `Options.gamma()` is `UnitDouble` and rejects anything
  outside `[0,1]`, so 1.0 — vanilla's Bright maximum — is the ceiling. **Worth a product decision:
  keep the honest pin, or drop the module.**
- **The walker cannot climb.** A build more than one block above the player's feet within a single
  action is refused and reported, not attempted. Real pathfinding (a walkable-node search over the
  world) is the next step if tall builds matter; the current steering is deliberately the cheap half.
- **No in-reach indicator.** The HUD says `STALLED` with a reason and a count, but not "these three
  are 20 blocks away and behind a wall". A per-distance breakdown is the obvious next refinement.
- **Hollow modules** that do nothing when clicked: `Anti-Trap`, `Auto Reconnect`, `Auto Eat`, and
  `Anti-Kick` (`PlayerModules.noteDisconnectReason` still has no caller).
- **~34 Corz modules** buildable under the no-mixin rule but never built.
- **No visual verification of anything, ever** — see the GPU note above.

---

## Related docs

- `MEMORY.md` (this folder) — condensed project memory; the **1.3.0**, **1.2.0** and **1.1.0** sections
  hold the full change lists.
- `README.md` (this folder) — how to install and use it, including the schematic workflow.
- `CHAT-SESSION.md` (this folder) — transcript of the sessions that produced 1.1.0, 1.2.0 and 1.3.0.
- `BRIEFING.md` (workspace root) — instruction-by-instruction status, limits, excluded features.
