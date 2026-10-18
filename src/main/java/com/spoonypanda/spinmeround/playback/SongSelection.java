package com.spoonypanda.spinmeround.playback;

import com.spoonypanda.spinmeround.CompactDisc;
import com.spoonypanda.spinmeround.Config;
import com.spoonypanda.spinmeround.CrankRecords;
import com.spoonypanda.spinmeround.SpinMeRound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import java.util.HashSet;
import java.util.Set;

public final class SongSelection {
    public static final SongSelection SILENT = new SongSelection("", 0);
    private static final Set<String> REPORTED_INVALID_SELECTIONS = new HashSet<>();
    public final String soundEventId;
    public final double bpm;

    public SongSelection(String soundEventId, double bpm) {
        this.soundEventId = soundEventId;
        this.bpm = bpm;
    }

    public static double readBpm(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("BPM", 99)) {
            return 0;
        }
        double bpmValue = tag.getDouble("BPM");
        return Double.isFinite(bpmValue) && bpmValue > 0 && bpmValue <= 1000 ? bpmValue : 0;
    }

    public static SongSelection resolveSong(String storedSelection, double bpm) {
        if (CrankRecords.CLEARED_SELECTION.equals(storedSelection)) {
            return SILENT;
        }
        try {
            if (storedSelection.isEmpty()) {
                if (!Config.defaultRecordDefinition.isEmpty()) {
                    return fromItemDefinition(Config.defaultRecordDefinition);
                }
                return Config.defaultSoundEventId.isEmpty() ? SILENT : new SongSelection(requireValidResourceId(Config.defaultSoundEventId), 0);
            }
            if (storedSelection.startsWith(CompactDisc.CUSTOM_SOUND_PREFIX)) {
                return new SongSelection(requireValidResourceId(storedSelection.substring(CompactDisc.CUSTOM_SOUND_PREFIX.length())), bpm);
            }
            SongSelection selection = fromItemDefinition(storedSelection);
            return new SongSelection(selection.soundEventId, bpm > 0 ? bpm : selection.bpm);
        } catch (Exception exception) {
            if (REPORTED_INVALID_SELECTIONS.add(storedSelection + "|" + Config.defaultRecordDefinition + "|" + Config.defaultSoundEventId)) {
                SpinMeRound.logger.warn("Invalid crank selection/default: {}", storedSelection, exception);
            }
            return SILENT;
        }
    }

    private static SongSelection fromItemDefinition(String definition) throws Exception {
        int tagStart = definition.indexOf('{');
        String itemId = tagStart < 0 ? definition : definition.substring(0, tagStart).trim();
        ResourceLocation id = new ResourceLocation(requireValidResourceId(itemId));
        Item item = Item.REGISTRY.getObject(id);
        if (!Item.REGISTRY.containsKey(id) || item == null) {
            throw new IllegalArgumentException("Unknown item " + itemId);
        }
        ItemStack stack = new ItemStack(item);
        if (tagStart >= 0) {
            stack.setTagCompound(JsonToNBT.getTagFromJson(definition.substring(tagStart)));
        }
        if (item == CompactDisc.INSTANCE) {
            String soundEventId = CompactDisc.getSoundEventId(stack);
            if (soundEventId.isEmpty()) {
                throw new IllegalArgumentException("Compact Disc needs a valid Sound tag");
            }
            return new SongSelection(soundEventId, readBpm(stack));
        }
        if (item instanceof ItemRecord) {
            // ItemRecord#getSound is stripped on dedicated servers in 1.12.2.
            SoundEvent recordSound = ObfuscationReflectionHelper.getPrivateValue(
                    ItemRecord.class, (ItemRecord) item, "field_185076_b");
            ResourceLocation recordSoundId = recordSound == null ? null : recordSound.getRegistryName();
            if (recordSoundId == null) {
                throw new IllegalArgumentException("Record has no registered sound: " + itemId);
            }
            return new SongSelection(recordSoundId.toString(), readBpm(stack));
        }
        throw new IllegalArgumentException("Not a record or Compact Disc: " + itemId);
    }

    private static String requireValidResourceId(String id) {
        if (id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid sound/item ID: " + id);
        }
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SongSelection)) {
            return false;
        }
        SongSelection song = (SongSelection) other;
        return soundEventId.equals(song.soundEventId) && Double.compare(bpm, song.bpm) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * soundEventId.hashCode() + Double.hashCode(bpm);
    }
}
