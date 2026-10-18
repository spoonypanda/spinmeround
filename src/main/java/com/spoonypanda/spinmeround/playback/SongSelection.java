package com.spoonypanda.spinmeround.playback;

import com.spoonypanda.spinmeround.CompactDisc;
import com.spoonypanda.spinmeround.Config;
import com.spoonypanda.spinmeround.SpinMeRound;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.ForgeRegistries;

public record SongSelection(String sound, double bpm) {
    public static final SongSelection SILENT = new SongSelection("", 0);
    private static String cachedRecord;
    private static String cachedSound;
    private static SongSelection cachedDefault = SILENT;

    public static SongSelection fromStack(ItemStack stack) {
        String sound = stack.getItem() instanceof CompactDisc ? CompactDisc.getSoundEventId(stack)
                : stack.getItem() instanceof RecordItem record ? record.getSound().getLocation().toString() : "";
        return new SongSelection(sound, CompactDisc.getBpm(stack));
    }

    public static SongSelection defaultSong() {
        String record = Config.RECORD.get();
        String sound = Config.SOUND.get();
        if (record.equals(cachedRecord) && sound.equals(cachedSound)) return cachedDefault;
        cachedRecord = record;
        cachedSound = sound;
        cachedDefault = SILENT;
        try {
            if (!record.isBlank()) {
                int nbtStart = record.indexOf('{');
                var id = ResourceLocation.tryParse(nbtStart < 0 ? record : record.substring(0, nbtStart));
                if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) throw new IllegalArgumentException("Unknown disc: " + record);
                ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(id));
                if (nbtStart >= 0) stack.setTag(TagParser.parseTag(record.substring(nbtStart)));
                cachedDefault = fromStack(stack);
                if (cachedDefault.sound.isEmpty()) throw new IllegalArgumentException("Default item does not select a sound: " + record);
            } else if (!sound.isBlank()) {
                if (sound.length() > 256 || !sound.contains(":") || ResourceLocation.tryParse(sound) == null) throw new IllegalArgumentException("Invalid sound: " + sound);
                cachedDefault = new SongSelection(sound, 0);
            }
        } catch (Exception exception) {
            cachedDefault = SILENT;
            SpinMeRound.LOGGER.warn("Invalid default crank song; playback will remain silent", exception);
        }
        return cachedDefault;
    }
}
