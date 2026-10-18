package com.spoonypanda.spinmeround.client;

import com.spoonypanda.spinmeround.CompactDisc;
import com.spoonypanda.spinmeround.SpinMeRound;
import java.util.Objects;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@SuppressWarnings("unused") // Forge discovers this subscriber and invokes its event handler.
@Mod.EventBusSubscriber(modid = SpinMeRound.MOD_ID, value = Side.CLIENT)
public final class ItemModels {
    @SubscribeEvent
    public static void register(ModelRegistryEvent event) {
        ResourceLocation discId = Objects.requireNonNull(CompactDisc.INSTANCE.getRegistryName(),
                "Compact Disc must have a registry name before models are registered");
        ModelLoader.setCustomModelResourceLocation(CompactDisc.INSTANCE, 0,
                new ModelResourceLocation(discId, "inventory"));
    }
}
