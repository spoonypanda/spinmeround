package com.spoonypanda.spinmeround;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> RECORD;
    public static final ForgeConfigSpec.ConfigValue<String> SOUND;
    public static final ForgeConfigSpec.ConfigValue<String> CLEAR_ITEM;
    public static final ForgeConfigSpec.IntValue VOLUME;
    public static final ForgeConfigSpec.BooleanValue LOOP;
    public static final ForgeConfigSpec.IntValue GRACE_TICKS;
    public static final ForgeConfigSpec.IntValue FADE_TICKS;
    public static final ForgeConfigSpec.BooleanValue FADE_BACKGROUND_MUSIC;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("playback");
        RECORD = builder.comment("Default music disc item ID. Empty means no default disc.").define("record", "");
        SOUND = builder.comment("Default sound event ID; used when record is empty.").define("sound", "");
        CLEAR_ITEM = builder.comment("Reusable item used to clear a crank's song.").define("clearItem", "minecraft:paper");
        VOLUME = builder.defineInRange("volume", 100, 0, 100);
        LOOP = builder.define("loop", true);
        GRACE_TICKS = builder.defineInRange("graceTicks", 4, 0, 200);
        FADE_TICKS = builder.defineInRange("fadeTicks", 6, 0, 200);
        FADE_BACKGROUND_MUSIC = builder.define("fadeBackgroundMusic", true);
        builder.pop();
        SPEC = builder.build();
    }

    private Config() {}
}
