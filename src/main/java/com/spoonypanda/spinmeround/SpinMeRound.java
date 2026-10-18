package com.spoonypanda.spinmeround;

import com.mojang.logging.LogUtils;
import com.spoonypanda.spinmeround.network.PlaybackNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(SpinMeRound.MOD_ID)
public final class SpinMeRound {
    public static final String MOD_ID = "spinmeround";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> COMPACT_DISC = ITEMS.register("compact_disc", CompactDisc::new);

    public SpinMeRound(FMLJavaModLoadingContext context) {
        var bus = context.getModEventBus();
        ITEMS.register(bus);
        bus.addListener(this::addCreativeItems);
        context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        PlaybackNetwork.init();
    }

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(COMPACT_DISC);
        }
    }
}
