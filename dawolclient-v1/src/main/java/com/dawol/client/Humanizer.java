package com.dawol.client;

import java.util.Random;

/**
 * Produces input values that look like a person produced them.
 *
 * <p>A machine that places a block every 250 ms with identical rotation and never
 * mis-clicks is trivially separable from a human. This class supplies the three things
 * that give a person away as a person: variable reaction time, imperfect aim, and
 * occasional error followed by correction.
 *
 * <p>All bounds are user-configurable and clamped, so a mis-set value cannot produce
 * inhuman output such as sub-50 ms actions or unbounded pauses.
 */
public final class Humanizer {

    private static final Random RANDOM = new Random();

    private Humanizer() {
    }

    /** Min/max millisecond window used for a placement pause. */
    public static long placementDelay() {
        return DelayRandomizer.sample(Toggles.buildDelayMinMs, Toggles.buildDelayMaxMs);
    }

    /**
     * Occasionally inserts a longer pause, the way attention lapses mid-task.
     *
     * <p>Returns 0 when no pause is drawn, otherwise a delay from the pause window. This
     * is what stops a build from reading as a perfectly regular metronome.
     */
    public static long occasionalPause() {
        if (RANDOM.nextDouble() >= Toggles.buildPauseChancePercent / 100.0D) {
            return 0L;
        }
        return DelayRandomizer.sample(Toggles.buildPauseMinMs, Toggles.buildPauseMaxMs);
    }

    /**
     * Angular error for an aim at {@code distance} blocks.
     *
     * <p>Error grows with distance, because a small angular miss at range is a large
     * positional miss — which is how human aim actually degrades.
     */
    public static float aimErrorDegrees(double distance) {
        double base = DelayRandomizer.sampleRotationJitter();
        double scaled = base * Math.max(1.0D, Math.min(3.0D, distance / 8.0D));
        return (float) scaled;
    }

    /** A random sign, so jitter is not biased in one direction. */
    public static float randomSign() {
        return RANDOM.nextBoolean() ? 1.0F : -1.0F;
    }

    /** Whether this action should deliberately go wrong. */
    public static boolean rollsMistake() {
        return RANDOM.nextDouble() < Toggles.buildMistakeChancePercent / 100.0D;
    }

    /** Which kind of mistake to make, when one is due. */
    public static Mistake rollMistakeType() {
        return switch (RANDOM.nextInt(3)) {
            case 0 -> Mistake.WRONG_BLOCK;
            case 1 -> Mistake.MISSED_CLICK;
            default -> Mistake.OVERSHOOT;
        };
    }

    /** Whether the player pauses to look at the result before continuing. */
    public static boolean rollsDoubleCheck() {
        return RANDOM.nextDouble() < Toggles.buildDoubleCheckChancePercent / 100.0D;
    }

    /** The kinds of error a build can make, all of which are recoverable by verification. */
    public enum Mistake {
        /** Places the currently selected block instead of the intended one. */
        WRONG_BLOCK,
        /** Fails to place at all, leaving the placement unsatisfied. */
        MISSED_CLICK,
        /** Rotates past the target, so the placement lands nowhere useful. */
        OVERSHOOT
    }
}