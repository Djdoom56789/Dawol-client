# TODO-BLOCKED — features refused under the zero-injection rule

These features were evaluated against Goobaa and Krypton's public module
lists and refused because they require a mixin, class transformer, access
widener, or packet injection to work. None will be built.

---

## Velocity / Anti-Knockback
**Reason:** knockback is applied server-side via the `Entity.knockback()`
call which writes to the entity's velocity. The client receives a
`SET_ENTITY_MOTION` packet and stores it. Reducing or zeroing that velocity
requires either (a) intercepting the packet write — packet injection, or
(b) overriding `Entity.travel()` — mixin. Neither is allowed.

---

## Fly / Speed / Timer / TickShift
**Reason:** all three require overriding the player's movement system
(`LocalPlayer.travel()`, `ClientInput`, tick rate). Every reliable
implementation requires a mixin into the movement or physics loop.

---

## NoSlow
**Reason:** "no slow" (full speed while using items, blocking, etc.) works
by suppressing the `isSprinting` / `getSpeed` adjustments applied in
`LocalPlayer.travel()` — mixin required.

---

## Scaffold / TowerUp
**Reason:** scaffold places blocks beneath the player's feet while they
walk, which requires intercepting the block-hit detection or fabricating
block-face hit results that do not correspond to the player's actual
crosshair direction. This is packet manipulation.

---

## FakeLag / PacketDelay
**Reason:** requires buffering outgoing packets before they are sent,
which requires intercepting the packet pipeline — injection.

---

## KillAura (rotation-packet variant)
**Reason:** rotating to a target without the player physically looking
requires sending a rotation update packet whose yaw/pitch differ from
the camera's. TriggerBot (already built) fires only when the entity is
genuinely under the crosshair — no fake rotation.

---

## Freecam
**Reason:** detached freecam needs either a separate camera entity
(server-visible, violates the no-server-detection goal) or overriding
`Camera.setup()` to use a different position — mixin. The HANDOFF
documents this in detail: `Camera.position()` is the camera entity's
position; there are no public controls to detach it.

---

## AutoCrystal (full automation)
**Reason:** competitive AutoCrystal breaks and re-places end crystals
faster than a human tick rate allows. Making the timing competitive
requires either manipulating packet send timing (FakeLag) or overriding
the item use cooldown — both forbidden.
TriggerBot firing on an end crystal that the player already placed is
fine and already covered by the TriggerBot module.

---

## AnchorAura / BedAura
**Reason:** both need the same sub-tick timing as AutoCrystal to be
effective. The placement + activation loop within a single game tick
requires packet-rate manipulation.

---

## Burrow / Anti-Suffocation
**Reason:** burrow requires placing a block inside the player's bounding
box (normally rejected by the server), which needs a fake block-hit that
the server must accept — a form of packet manipulation.

---

## Xray (chunk-rendering patch)
**Reason:** patching the chunk renderer to skip opaque blocks requires
overriding `ChunkRenderDispatcher` or `ChunkCompiler` — mixin. The
OreSimModule (already built) uses block-scan and seed-simulation instead,
which is pure client-side math with no rendering override.

---

## Reach Extension
**Reason:** vanilla reach is enforced by `ServerGamePacketListenerImpl`
server-side. Extending it client-side means sending interactions at
positions the server rejects — a known anti-cheat signal regardless of
how the client sends it.

---

## AutoAnchor / BedAura (block-placement variant)
Already listed under AnchorAura above.

---

## AntiKnockback
Already listed under Velocity above.
