package com.spoonypanda.spinmeround.playback;

/** Server-owned elapsed music time, advancing only while music is sustained or fading. */
public final class PlaybackClock {
    private int idleTicks;
    private float gain;
    private long ticks;

    public PlaybackClock(long ticks) {
        this.ticks = Math.max(0, ticks);
    }

    public void tick(boolean turning, int graceTicks, int fadeTicks) {
        idleTicks = turning ? 0 : Math.min(idleTicks + 1, graceTicks + 1);
        boolean sustain = turning || (gain > 0 && idleTicks <= graceTicks);
        float step = fadeTicks == 0 ? 1 : 1.0F / fadeTicks;
        gain = sustain ? Math.min(1, gain + step) : Math.max(0, gain - step);
        if (gain < 0.0001F) gain = 0;
        if (gain > 0 && ticks < Long.MAX_VALUE) ticks++;
    }

    public float gain() { return gain; }
    public long ticks() { return ticks; }
}
