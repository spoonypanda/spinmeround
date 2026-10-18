package com.spoonypanda.spinmeround;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A reusable song selector with resource-pack sound and optional note timing. */
public final class CompactDisc extends Item {
    public CompactDisc() {
        super(new Item.Properties().stacksTo(1));
    }

    public static String getSoundEventId(ItemStack stack) {
        if (!(stack.getItem() instanceof CompactDisc) || stack.getTag() == null) return "";
        String sound = stack.getTag().getString("Sound");
        return sound.length() <= 256 && sound.contains(":") && ResourceLocation.tryParse(sound) != null ? sound : "";
    }

    public static double getBpm(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().contains("BPM", Tag.TAG_ANY_NUMERIC)) return 0;
        double bpm = stack.getTag().getDouble("BPM");
        return Double.isFinite(bpm) && bpm > 0 && bpm <= 1000 ? bpm : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String sound = getSoundEventId(stack);
        tooltip.add(sound.isEmpty() ? Component.translatable("tooltip.spinmeround.blank_disc")
                : Component.translatable("tooltip.spinmeround.sound", sound));
        double bpm = getBpm(stack);
        if (bpm > 0) tooltip.add(Component.translatable("tooltip.spinmeround.bpm", bpm));
    }
}
