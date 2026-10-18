package com.spoonypanda.spinmeround.playback;

import com.spoonypanda.spinmeround.SpinMeRound;
import net.minecraft.tileentity.TileEntity;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

public final class CrankAccess {
    private static final Set<Class<?>> REPORTED_FAILURES = new HashSet<>();
    private static final ClassValue<Field> ROTATION_FIELD = new ClassValue<Field>() {
        @Override
        protected Field computeValue(@Nonnull Class<?> type) {
            for (Class<?> base = type; base != null; base = base.getSuperclass()) {
                try {
                    Field field = base.getDeclaredField("rotation");
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) { }
            }
            throw new IllegalStateException("Missing AE2 crank rotation field: " + type.getName());
        }
    };

    public static boolean isTurning(TileEntity tile) {
        try {
            if (ManualMillAccess.isManualMill(tile)) {
                return ManualMillAccess.isTurning(tile);
            }
            return ROTATION_FIELD.get(tile.getClass()).getInt(tile) > 0;
        } catch (Exception exception) {
            logActivityFailure(tile, exception);
            return false;
        }
    }

    public static float playbackGainLimit(TileEntity tile) {
        if (!ManualMillAccess.isManualMill(tile)) {
            return 1;
        }
        try {
            return ManualMillAccess.playbackGainLimit(tile);
        } catch (Exception exception) {
            logActivityFailure(tile, exception);
            return 0;
        }
    }

    private static void logActivityFailure(TileEntity tile, Exception exception) {
        if (REPORTED_FAILURES.add(tile.getClass())) {
            SpinMeRound.logger.error("Cannot read hand crank activity", exception);
        }
    }
}
