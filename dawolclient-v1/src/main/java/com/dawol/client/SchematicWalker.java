package com.dawol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Walks the player to the block the builder is trying to place.
 *
 * <p>A build is bigger than an arm. Without this, everything past the first few blocks is
 * merely out of range and gets skipped, which the build reports as "finished" — the failure
 * mode that makes a builder look like it works when it does not.
 *
 * <p>Movement goes through the same key mappings a player's keyboard writes to, so the walk
 * is the game's own: normal acceleration, normal collision, normal step-up, normal fall
 * damage. Nothing moves the player directly, and no packet is built by hand. The cost of
 * that honesty is that this is a steering loop, not a pathfinder: it walks at the goal and
 * hops when something blocks it. That covers open ground, platforms, walls and interiors; it
 * cannot climb cliffs or cross a gap it has no way over. When it cannot get there it says so
 * and the caller moves on, rather than standing still looking busy.
 */
public final class SchematicWalker {

    /** What one step of walking did, which is all the caller needs to decide. */
    public enum Progress {
        /** Still walking; call again next tick. */
        MOVING,
        /** Standing where the goal asked for. */
        ARRIVED,
        /** Cannot get there, or should not try. Give up rather than grind. */
        BLOCKED
    }

    /** How close to the goal counts as standing there. */
    private static final double ARRIVED_DISTANCE = 0.7D;

    /**
     * Degrees the walker will turn per tick.
     *
     * <p>A person does not snap to face where they are going, and an instant 180 is both
     * visibly mechanical and a large rotation for the server to see in one tick.
     */
    private static final float TURN_PER_TICK = 16.0F;

    /** How far off the goal the walker may be pointed and still press forward. */
    private static final float ALIGNMENT_DEGREES = 55.0F;

    /** Ticks of no horizontal progress before the walker starts hopping. */
    private static final int HOP_AFTER_STUCK_TICKS = 5;

    /** Ticks of no progress at all before the walk gives up. */
    private static final int STUCK_TICKS = 35;

    /** Hard cap on one walk, so a bad goal cannot hold the build forever. */
    private static final int GIVE_UP_TICKS = 200;

    /**
     * How far below the player a goal may sit.
     *
     * <p>Dropping a few blocks is a normal part of walking around a build. Dropping further
     * means stepping off one, and this walker has no way back up, so it refuses instead.
     */
    private static final int MAX_DROP = 3;

    /** How far above the player a goal may sit: one step, which vanilla can walk and hop up. */
    private static final int MAX_CLIMB = 1;

    /**
     * Ticks the walker stands down after the player touches a movement key themselves.
     *
     * <p>The key mappings are shared, so driving them means fighting whoever else is driving
     * them. Backing off for a moment keeps the module from yanking control away mid-jump.
     */
    private static final int YIELD_TICKS = 40;

    /** Blocks that are never worth walking into, whatever the build wants. */
    private static final Block[] HAZARDS = {
            Blocks.LAVA, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.CACTUS, Blocks.MAGMA_BLOCK,
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.POWDER_SNOW, Blocks.SWEET_BERRY_BUSH
    };

    private int ticks;
    private int stuck;
    private int yielding;
    private double lastX;
    private double lastZ;
    private boolean pressedForward;
    private boolean pressedJump;

    /**
     * Walks one tick toward {@code goal}.
     *
     * @param goalY the level the caller wants to be standing at, used to refuse walks the
     *              walker cannot complete rather than starting them
     */
    public Progress step(Minecraft mc, Vec3 goal, int goalY) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || goal == null) {
            release(mc);
            return Progress.BLOCKED;
        }

        if (yieldToPlayer(mc, player)) {
            return Progress.MOVING;
        }

        double dx = goal.x - player.getX();
        double dz = goal.z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= ARRIVED_DISTANCE) {
            release(mc);
            return Progress.ARRIVED;
        }

        // The walker only ever moves sideways. A goal above or below the player is a climb or
        // a drop it cannot undo, so it is refused before any key is pressed.
        int feet = BlockPos.containing(player.getX(), player.getY(), player.getZ()).getY();
        if (goalY > feet + MAX_CLIMB || goalY < feet - MAX_DROP) {
            release(mc);
            return Progress.BLOCKED;
        }

        if (++ticks > GIVE_UP_TICKS) {
            release(mc);
            return Progress.BLOCKED;
        }

        // Progress is measured after a few ticks of acceleration, never on the first one.
        double moved = Math.abs(player.getX() - lastX) + Math.abs(player.getZ() - lastZ);
        lastX = player.getX();
        lastZ = player.getZ();
        if (ticks > 3 && moved < 0.03D) {
            stuck++;
        } else {
            stuck = 0;
        }
        if (stuck > STUCK_TICKS) {
            release(mc);
            return Progress.BLOCKED;
        }

        if (hazardous(mc.level, player, dx, dz, distance)) {
            release(mc);
            return Progress.BLOCKED;
        }

        return steer(mc, player, goal);
    }

    /** What the walker wants to do this tick. */
    public record Step(float turn, boolean forward, boolean jump) {
    }

    /**
     * The whole steering decision, as a function of where the player is and where they look.
     *
     * <p>Separated from the client so the behaviour can be checked directly: the turn rate is
     * capped, forward is only pressed when the goal is roughly ahead, and the hop only happens
     * once the walk has actually stalled on the ground. Those are the rules that decide whether
     * the player arrives or grinds against a wall, and none of them need a world to verify.
     *
     * @param stuck ticks of no horizontal progress so far
     */
    public static Step decide(float currentYaw, Vec3 eye, Vec3 goal, boolean onGround, int stuck) {
        // The goal's own height is ignored: walking is flat, and whether the player can get up
        // to it is decided before any key is pressed.
        float delta = wrapDegrees(SchematicPlacer.yawTo(eye, new Vec3(goal.x, eye.y, goal.z)) - currentYaw);
        boolean aligned = Math.abs(delta) < ALIGNMENT_DEGREES;
        boolean hop = aligned && stuck >= HOP_AFTER_STUCK_TICKS && onGround;
        return new Step(clamp(delta, TURN_PER_TICK), aligned, hop);
    }

    /** Turns toward the goal and presses the keys that get there. */
    private Progress steer(Minecraft mc, LocalPlayer player, Vec3 goal) {
        Step step = decide(player.getYRot(), player.getEyePosition(), goal, player.onGround(), stuck);
        player.setYRot(player.getYRot() + step.turn());
        press(mc.options.keyUp, step.forward(), true);
        // Hopping is what gets over a one-block step, and what unsticks a walk that is pressed
        // against something the goal is behind.
        press(mc.options.keyJump, step.jump(), false);
        return Progress.MOVING;
    }

    /**
     * Steps aside when the player is driving, and reports whether the walk sat this tick out.
     *
     * <p>A key that is down without this class having pressed it was pressed by the person at
     * the keyboard, and their input wins.
     */
    private boolean yieldToPlayer(Minecraft mc, LocalPlayer player) {
        if (yielding > 0) {
            yielding--;
            release(mc);
            return true;
        }

        // The default binding is a good enough proxy for "is a movement key held": this only
        // has to be right for the keys the walker itself drives.
        if (keyHeld(mc.options.keyUp)
                || keyHeld(mc.options.keyDown)
                || keyHeld(mc.options.keyLeft)
                || keyHeld(mc.options.keyRight)
                || keyHeld(mc.options.keyJump)) {
            yielding = YIELD_TICKS;
            release(mc);
            return true;
        }
        return false;
    }

    /**
     * Whether a movement key is held by anyone.
     *
     * <p>Checked physically rather than through the mapping, because the walker writes to the
     * mapping itself and would otherwise mistake its own presses for the player's. A rebound
     * key is not seen here, which costs one tick of the player's input at worst.
     */
    private static boolean keyHeld(net.minecraft.client.KeyMapping mapping) {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(mapping.getDefaultKey().getValue());
    }

    /** True when the ground being walked onto is not worth walking onto. */
    private static boolean hazardous(Level level, LocalPlayer player, double dx, double dz, double distance) {
        double aheadX = player.getX() + dx / distance * 0.6D;
        double aheadZ = player.getZ() + dz / distance * 0.6D;
        BlockPos ahead = BlockPos.containing(aheadX, player.getY(), aheadZ);
        return hazard(level, ahead) || hazard(level, ahead.below());
    }

    private static boolean hazard(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        for (Block candidate : HAZARDS) {
            if (block == candidate) {
                return true;
            }
        }
        return false;
    }

    /** Presses or releases one key, remembering only the ones this class pressed. */
    private void press(net.minecraft.client.KeyMapping mapping, boolean down, boolean isForward) {
        mapping.setDown(down);
        if (isForward) {
            pressedForward = down;
        } else {
            pressedJump = down;
        }
    }

    /** Lets go of everything the walker is holding, and forgets the walk. */
    public void release(Minecraft mc) {
        if (mc != null && mc.options != null) {
            if (pressedForward) {
                mc.options.keyUp.setDown(false);
            }
            if (pressedJump) {
                mc.options.keyJump.setDown(false);
            }
        }
        pressedForward = false;
        pressedJump = false;
        ticks = 0;
        stuck = 0;
    }

    /** Lets go from anywhere, for callers that have no client handle. */
    public void release() {
        release(Minecraft.getInstance());
    }

    /** Shortest signed turn from one angle to another, in degrees. */
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    /** Clamps a turn to the walker's per-tick rate. */
    public static float clamp(float degrees, float limit) {
        return Math.max(-limit, Math.min(limit, degrees));
    }
}
