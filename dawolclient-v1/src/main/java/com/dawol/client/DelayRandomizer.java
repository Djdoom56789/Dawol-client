package com.dawol.client;

import java.util.Random;

/**
 * Humanises the timing of every automated action.
 *
 * <p>A fixed interval is one of the cheapest things a server can fingerprint: an action
 * that repeats at exactly N ticks, forever, is not something people produce. Real input
 * varies because reaction time varies. This class supplies that variance, and makes its
 * bounds user-configurable.
 *
 * <p>Bounds are clamped to a floor and ceiling so a mis-set range cannot turn an automated
 * action into a packet spray, which would be a far louder signature than the fixed delay
 * it replaced.
 */
public final class DelayRandomizer {

    /** Absolute floor in milliseconds, regardless of what the user configures. */
    public static final long MIN_FLOOR_MS = 50L;

    /** Absolute ceiling in milliseconds, so a bad config cannot stall a module forever. */
    public static final long MAX_CEILING_MS = 10_000L;

    private static final Random RANDOM = new Random();

    private long nextReadyAt = 0L;

    private DelayRandomizer() {
    }

    public static DelayRandomizer create() {
        return new DelayRandomizer();
    }

    /**
     * Draws an inclusive delay in milliseconds from {@code [min, max]}, clamped to the safe
     * floor and ceiling. An inverted pair is normalised rather than rejected, so a bad
     * config degrades to a fixed delay instead of misbehaving.
     */
    public static long sample(long min, long max) {
        // Both ends are clamped into the safe window after normalising the pair, so neither
        // the early return nor the draw can hand back a value outside it.
        long lo = Math.max(MIN_FLOOR_MS, Math.min(MAX_CEILING_MS, Math.min(min, max)));
        long hi = Math.min(MAX_CEILING_MS, Math.max(MIN_FLOOR_MS, Math.max(min, max)));

        if (hi <= lo) {
            return lo;
        }
        return lo + (long) (RANDOM.nextDouble() * (hi - lo));
    }

    /** Draws from the global auto-delay window. */
    public static long sampleAuto() {
        return sample(Toggles.autoDelayMinMs, Toggles.autoDelayMaxMs);
    }

    /** Draws a reach jitter offset in blocks from the configured millimetre window. */
    public static double sampleReachJitter() {
        long lo = Math.min(Toggles.reachJitterMinMm, Toggles.reachJitterMaxMm);
        long hi = Math.max(Toggles.reachJitterMinMm, Toggles.reachJitterMaxMm);
        if (hi <= lo) {
            return lo / 1000.0D;
        }
        return (lo + (long) (RANDOM.nextDouble() * (hi - lo))) / 1000.0D;
    }

    /** Draws a rotation jitter offset in degrees from the configured milli-degree window. */
    public static float sampleRotationJitter() {
        long lo = Math.min(Toggles.rotationJitterMinMilliDeg, Toggles.rotationJitterMaxMilliDeg);
        long hi = Math.max(Toggles.rotationJitterMinMilliDeg, Toggles.rotationJitterMaxMilliDeg);
        if (hi <= lo) {
            return lo / 1000.0F;
        }
        return (lo + (long) (RANDOM.nextDouble() * (hi - lo))) / 1000.0F;
    }

    /**
     * Whether an action may fire now. When it may, the next window is scheduled
     * immediately, so callers do not manage the timer themselves.
     */
    public boolean ready(long min, long max) {
        long now = System.currentTimeMillis();
        if (now < nextReadyAt) {
            return false;
        }
        nextReadyAt = now + sample(min, max);
        return true;
    }

    /** Convenience: {@link #ready} using the global auto window. */
    public boolean readyAuto() {
        return ready(Toggles.autoDelayMinMs, Toggles.autoDelayMaxMs);
    }

    public void reset() {
        nextReadyAt = 0L;
    }

    /** Milliseconds until the next window opens; 0 when ready now. */
    public long remainingMillis() {
        return Math.max(0L, nextReadyAt - System.currentTimeMillis());
    }
}