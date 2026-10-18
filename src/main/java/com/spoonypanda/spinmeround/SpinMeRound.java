package com.spoonypanda.spinmeround;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = SpinMeRound.MOD_ID, name = "Spin Me Round", version = "1.0",
        acceptedMinecraftVersions = "[1.12.2]")
public final class SpinMeRound {
    public static final String MOD_ID = "spinmeround";
    public static Logger logger;
    @SidedProxy(clientSide = "com.spoonypanda.spinmeround.client.ClientProxy",
            serverSide = "com.spoonypanda.spinmeround.CommonProxy")
    public static CommonProxy proxy;
    @SuppressWarnings("unused") // Forge invokes this handler during mod pre-initialization.
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        Config.load(event.getSuggestedConfigurationFile());
        CrankRecords.init();
        proxy.preInit();
    }
}
