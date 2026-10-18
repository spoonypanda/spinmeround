package com.spoonypanda.spinmeround;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class Config {
    public static String defaultRecordDefinition;
    public static String defaultSoundEventId;
    public static String clearItemId;

    /** Normalized audio multiplier; the configuration exposes this as 0 to 100. */
    public static float volumeMultiplier;
    public static boolean loopPlayback;
    public static int fadeTicks;
    public static int graceTicks;
    public static boolean fadeBackgroundMusic;

    private Config() {
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        defaultRecordDefinition = config.getString("record", "playback", "",
                "Record item ID, optionally followed by item NBT. Example: "
                        + "spinmeround:compact_disc{Sound:\"mymusic:spinmeround_song\"}. "
                        + "This is an example of a compact disc item created with crafttweaker."
                        + "Leave empty to use the sound setting instead.").trim();
        defaultSoundEventId = config.getString("sound", "playback", "",
                "Sound event resource ID from sounds.json; used when record is empty. Leave both empty for silence.").trim();
        clearItemId = config.getString("clearItem", "playback", "minecraft:paper",
                "Item registry ID used to clear a crank's song by right-clicking. The item is not consumed.").trim();
        if (config.hasKey("playback", "volume") && config.getCategory("playback").get("volume").getType()
                == net.minecraftforge.common.config.Property.Type.DOUBLE) {
            double previous = config.getCategory("playback").remove("volume").getDouble(1.0);
            config.get("playback", "volume", 100).set((int) Math.round(Math.max(0, Math.min(1, previous)) * 100));
        }
        volumeMultiplier = config.getInt("volume", "playback", 100, 0, 100,
                "Playback volume from 0 (silent) to 100 (full volume), also affected by Records and Master sliders.") / 100.0F;
        loopPlayback = config.getBoolean("loop", "playback", true,
                "Loop the track when it reaches the end.");
        fadeTicks = config.getInt("fadeTicks", "playback", 6, 0, 40,
                "Fade duration when starting or stopping, in ticks (20 ticks = 1 second).");
        graceTicks = config.getInt("graceTicks", "playback", 4, 0, 20,
                "Keep music running across brief gaps between crank turns, in ticks.");
        fadeBackgroundMusic = config.getBoolean("fadeBackgroundMusic", "playback", true,
                "Fade Minecraft background music out while a nearby crank plays and back in afterward.");
        if (config.hasChanged()) {
            config.save();
        }
    }
}
