package com.spoonypanda.spinmeround.playback;
public final class PlaybackClock {
    private static final double TICKS_PER_MINUTE = 20 * 60;
    private int idleTicks;
    private float fadeGain;
    private long playheadTicks;

    public PlaybackClock(long playheadTicks) {
        this.playheadTicks = Math.max(0, playheadTicks);
    }

    public void tick(boolean turning, int graceTicks, int fadeTicks) {
        tick(turning, graceTicks, fadeTicks, 1);
    }

    /** A machine can lower the gain limit as its remaining motion runs down. */
    public void tick(boolean turning, int graceTicks, int fadeTicks, float gainLimit) {
        idleTicks = turning ? 0 : Math.min(idleTicks + 1, graceTicks + 1);
        boolean shouldSustainPlayback = turning || (fadeGain > 0 && idleTicks <= graceTicks);
        float fadeStepPerTick = fadeTicks == 0 ? 1 : 1.0F / fadeTicks;
        fadeGain = shouldSustainPlayback ? Math.min(1, fadeGain + fadeStepPerTick) : Math.max(0, fadeGain - fadeStepPerTick);
        fadeGain = Math.min(fadeGain, Math.max(0, Math.min(1, gainLimit)));
        if (fadeGain < 0.0001F) {
            fadeGain = 0;
        }
        if (fadeGain > 0 && playheadTicks < Long.MAX_VALUE) {
            playheadTicks++;
        }
    }

    public float fadeGain() {
        return fadeGain;
    }

    public long playheadTicks() {
        return playheadTicks;
    }

    public static long beatIndexAt(long playheadTicks, double bpm) {
        return bpm > 0 ? (long) Math.floor(Math.max(0, playheadTicks) * (bpm / TICKS_PER_MINUTE)) : -1;
    }
}
