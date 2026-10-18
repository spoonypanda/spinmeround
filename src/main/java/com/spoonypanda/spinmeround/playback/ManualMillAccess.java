package com.spoonypanda.spinmeround.playback;

import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import net.minecraft.tileentity.TileEntity;

/** Optional access to Extra Utilities 2 without loading its classes when absent. */
public final class ManualMillAccess {
    private static final String TILE_CLASS = "com.rwtema.extrautils2.tile.TilePowerHandCrank";
    private static final ClassValue<Boolean> MANUAL_MILL_TYPES = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(@Nonnull Class<?> type) {
            for (Class<?> base = type; base != null; base = base.getSuperclass()) {
                if (TILE_CLASS.equals(base.getName())) {
                    return true;
                }
            }
            return false;
        }
    };
    private static final ClassValue<TimerFields> TIMER_FIELDS = new ClassValue<TimerFields>() {
        @Override
        protected TimerFields computeValue(@Nonnull Class<?> type) {
            try {
                Field timer = type.getField("TIME");
                Field value = timer.getType().getField("value");
                return new TimerFields(timer, value);
            } catch (NoSuchFieldException exception) {
                throw new IllegalStateException("Missing Extra Utilities 2 Manual Mill activity timer", exception);
            }
        }
    };

    public static boolean isManualMill(TileEntity tile) {
        return tile != null && MANUAL_MILL_TYPES.get(tile.getClass());
    }

    static boolean isTurning(TileEntity tile) throws IllegalAccessException {
        return remainingSpin(tile) > 0;
    }

    static float playbackGainLimit(TileEntity tile) throws IllegalAccessException {
        float spin = remainingSpin(tile);
        // Match the mill's full-power threshold, then fade as its remaining spin falls.
        return Float.isFinite(spin) ? Math.max(0, Math.min(1, spin / 0.5F)) : 0;
    }

    private static float remainingSpin(TileEntity tile) throws IllegalAccessException {
        TimerFields fields = TIMER_FIELDS.get(tile.getClass());
        return fields.value.getFloat(fields.timer.get(tile));
    }

    private static final class TimerFields {
        private final Field timer;
        private final Field value;

        private TimerFields(Field timer, Field value) {
            this.timer = timer;
            this.value = value;
        }
    }
}
