# DawolClient — folder guide

Everything in this folder, what it is, and how to use it.

**Current build: `dawolclient-1.3.0.jar`** · 169,099 B · sha256 `963dccaf614ce419558f3f0f2350eebbf8a5804a6a15afb0d8304e8595a7f4e0`

---

## What's in this folder

| File | What it is |
|---|---|
| `dawolclient-1.3.0.jar` | The mod. This is the file you install. |
| `README.md` | This guide. |
| `MEMORY.md` | Project memory. The `1.1.0`, `1.2.0` and `1.3.0` sections hold the full list of what was found and fixed. |
| `HANDOFF.md` | Handoff brief for the next session or agent: state, build recipe, gotchas, open items. |
| `CHAT-SESSION.md` | Full transcript: the 1.1.0 debug sweep, the 1.2.0 schematic load path and the 1.3.0 walking work. |

The 1.0.0, 1.1.0, 1.2.0 and stale `(1)` builds were removed as they were superseded — two jars with
the same mod id in one `mods\` folder will not load.

---

## Installing it

Requires **Minecraft 26.3** with **Fabric Loader 0.19.5 or newer**, **Fabric API 0.162.0+26.3**,
and **Java 25**.

1. Put `dawolclient-1.3.0.jar` in your `.minecraft\mods\` folder, inside your Fabric 26.3 profile.
2. Put the matching **Fabric API** jar there too — the mod depends on it.
3. Launch. The log should show `[dawol] initialised for Minecraft 26.3`.

Config is written to `config\dawolclient.properties` next to your mods folder. The old
`gtrace.properties` is no longer read; delete it if it is lying around.

> **Not yet loaded in a real profile on this machine.** There is no `.minecraft` install here, so
> the jar has only ever been built and tested headlessly — never launched into a world from this
> folder. First in-game run is still the untested step.

---

## The schematic builder

It builds from **external Litematica `.litematic` files**. Nothing has to be exported by hand or
converted: the files Litematica already writes are what it reads.

**Where the files go.** `<game directory>\schematics\`. The folder is created the first time the
browser opens. Subfolders are searched three levels deep, so a per-project layout works.

**Loading one.** Press **L** in game. The browser lists every `.litematic` and `.schem` it finds,
newest names sorted; click a row to load it. The panel on the right shows the schematic's own
declared name, its format, its size in blocks, how many placements it has, how many sub-regions it
has, and what it needs.

**Anchoring.** Block positions in the file are relative to the schematic's own origin, so the build
needs somewhere to go. Loading anchors it at your feet plus the `Build: offset X/Y/Z` settings
(RSHIFT overlay). To move a build that is already loaded, walk to the spot and press **Anchor
here** — the file is not re-read, so this is instant.

**Running it.** Turn **Schematic Builder** on, either from the overlay or the button in the
browser. The HUD then carries a line with the file name, its dimensions, the state and
`remaining/queued`.

| State | Meaning |
|---|---|
| `RUNNING` | Placing. |
| `WALKING` | On its way to the next block, because it was out of reach. |
| `COMPLETE` | Every placement the file asked for is satisfied in the world. |
| `STALLED` | Work is left and it cannot do it, with the reason and how many blocks: no material, out of reach, nothing to build against, or something else standing there. |
| `IDLE` | Loaded and anchored, but not placing right now. |
| `NONE` | Nothing loaded. |

**How it behaves:**

- **It walks to its work.** Anything past the vanilla 4.5-block reach, it walks to: it presses the
  same movement keys you do, so it moves with normal physics, walks around what it can and hops over
  a one-block step. It is a steering loop, not a pathfinder — it cannot climb a cliff, cross a gap
  or find its way round an obstacle, and when it cannot get somewhere it says `STALLED` with a count
  rather than walking in circles.
- **It places blocks in the orientation the file asks for.** Stairs face the way the schematic says,
  logs lie on the axis it says, wall torches go on walls. It works this out by asking the game itself
  what each possible click would produce, rather than by guessing.
- **It builds over what is already there.** A block standing where the schematic wants a different
  one is broken first, then replaced. Turn `Build: replace blocks` off if you would rather it left
  the terrain alone.
- **It only places what is in your hotbar.** The materials table marks every block with
  `have 0` — those are what stall a build. Put the blocks in the hotbar and the builder re-plans
  itself automatically, without you touching anything.
- **It stands still while a screen is open**, including its own browser, and while you are holding a
  movement key.
- **The mistakes are deliberate.** Some placements land as the wrong block, miss, or overshoot.
  Every one is caught by a verification pass and re-queued, so a slip costs time rather than
  correctness.
- **Formats:** Litematica `.litematic` and Sponge v2 `.schem`. Not MCEdit `.schematic`, not vanilla
  `.nbt` structures, not Sponge v3 files that store `BlockData`. Files the client cannot read are
  rejected with a message rather than half-loaded.
- **Modded blocks are skipped and counted** — the browser shows `skipped: N unsupported blocks` when
  a file references anything this version does not have.
- **A few blocks come out in a state the file did not ask for** — water logging, a fence connecting
  to what is beside it. Those cannot be argued with, so they are placed, accepted and counted; the
  browser reports them as `approximate`.
- **One load is capped at 2,000,000 placements.** Past that the overflow is counted and reported as
  `truncated`, so a build is never silently short.

---

## Controls

Per `MEMORY.md`:

- **RSHIFT** — module overlay (module list, categories, settings).
- **L** — schematic browser.
- **G** — toggle tracers.

Through-walls, block ESP, stash and combat modules all default **off**. Tracers default on.

---

## Rebuilding it

```powershell
. 'C:\Users\david\OneDrive\Documents\deepseek-harness\default-workspace\.tools\env.ps1'
cd 'C:\Users\david\OneDrive\Documents\deepseek-harness\default-workspace\gtrace'
gradle build --offline --console=plain
```

Output: `gtrace\build\libs\dawolclient-<version>.jar`. Ten self-test suites run as part of `build`.

**Cutting a new version:** the number lives in **two files** and both must change, or the jar name
and the mod's own metadata drift apart:

- `gtrace\build.gradle` → `version = '1.3.0'`
- `gtrace\src\main\resources\fabric.mod.json` → `"version": "1.3.0"`

Then rebuild and copy the new jar here.

**Build gotcha:** the project lives on OneDrive, which locks `gtrace\build`. It bites in three
slightly different ways: `Unable to delete directory ...\build\classes\java\main`,
`Failed to delete some children`, and `Failed to clean up stale outputs`. All three mean the same
thing. Fix: `gradle --stop`, delete `build\` and `selftest\build`, rebuild. Never run two gradle
builds at once.

**Another gotcha:** do not pipe gradle's live output into `Select-String`/`Select-Object`. The
pipeline can buffer and the command reports a timeout with no output even though the build
succeeded. Redirect to a file (`gradle build *> out.log`) and read the file — and note that `*>`
writes UTF-16, so convert it before grepping.

---

## Known limits and open decisions

- **Freecam — not built, needs your call.** True detached freecam requires a mixin, which is
  forbidden here. The alternatives are a passive zoom-out camera (undetectable, but not real
  freecam) or moving the camera entity (server-visible). Detail in `HANDOFF.md`.
- **Fullbright — honest but capped.** `Options.gamma()` rejects anything outside `[0,1]`, so the
  module now pins vanilla's maximum brightness instead of pretending to lift the ceiling. Making
  caves bright needs a lightmap mixin. Keep it as-is, or drop the module?
- **The walker cannot climb.** Anything more than one block above the player's feet inside a single
  action is refused and reported. Tall builds need you to reposition, or need real pathfinding to be
  written.
- **Hollow modules** that do nothing when clicked: `Anti-Trap`, `Auto Reconnect`, `Auto Eat`,
  `Anti-Kick`.
- **ESP cannot see unloaded chunks.** While flying, the server only sends chunks near you; no
  setting changes that.
- **Nothing has ever been visually verified — including the schematic browser.** The AMD Radeon
  840M on this machine dies with `0xC0000005` right after GL context creation, before any world
  renders. Geometry, plumbing, NBT parsing, the load path, the click geometry and the steering rules
  are all covered by the suites and by a headless smoke run; the *screen* has never been drawn. If
  something looks wrong, say what you see.
- **The builder has not yet run against a genuine Litematica export in a real world.** The parser is
  covered against real file shapes (multiple regions, negative sizes, wide palettes, cross-long
  indices, air and modded entries, truncation), the load path is smoke-tested end to end, and the
  walking and placement geometry are covered headlessly — but the first real build is still yours to
  try. Split a large build up; big regional exports are the case most likely to surface something.
