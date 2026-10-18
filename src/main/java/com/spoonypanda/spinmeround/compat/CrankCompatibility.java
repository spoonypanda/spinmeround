package com.spoonypanda.spinmeround.compat;

import com.spoonypanda.spinmeround.SpinMeRound;
import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.registries.ForgeRegistries;

/** No optional mod classes appear in descriptors, so absent mods cannot break class loading. */
public final class CrankCompatibility {
    public enum Kind { AE2, CREATE, GENERATOR }

    private static final ClassValue<Optional<Field>> ACTIVITY = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> type) {
            String name = type.getName().startsWith("appeng.") ? "rotation" : "inUse";
            for (Class<?> base = type; base != null; base = base.getSuperclass()) {
                try {
                    Field field = base.getDeclaredField(name);
                    if (field.getType() != int.class || !field.trySetAccessible()) break;
                    return Optional.of(field);
                } catch (NoSuchFieldException ignored) {
                    // Create subclasses can inherit the hand crank's timer.
                }
            }
            SpinMeRound.LOGGER.error("Cannot read crank activity for {}: missing int {}. Integration disabled for this class.", type.getName(), name);
            return Optional.empty();
        }
    };

    public static Kind kind(BlockState state) {
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return null;
        return switch (id.toString()) {
            case "ae2:crank" -> Kind.AE2;
            case "create:hand_crank" -> Kind.CREATE;
            case "hand_crank_generator:hand_crank_generator" -> Kind.GENERATOR;
            default -> null;
        };
    }

    public static boolean isTurning(BlockEntity blockEntity) {
        var field = ACTIVITY.get(blockEntity.getClass());
        if (field.isEmpty()) return false;
        try {
            return field.get().getInt(blockEntity) > 0;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Crank timer became inaccessible", exception);
        }
    }

    /** Generator 1.0.0 advances this property on each actual turn; it has no spin timer. */
    public static int generatorStep(BlockState state) {
        var property = state.getBlock().getStateDefinition().getProperty("blockstate");
        if (!(property instanceof IntegerProperty step)) {
            throw new IllegalStateException("Hand Crank Generator is missing its blockstate property");
        }
        return state.getValue(step);
    }

    private CrankCompatibility() {}
}
