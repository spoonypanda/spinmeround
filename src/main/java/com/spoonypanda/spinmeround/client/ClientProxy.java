package com.spoonypanda.spinmeround.client;

import com.spoonypanda.spinmeround.CommonProxy;
import com.spoonypanda.spinmeround.CrankRecords;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

@SuppressWarnings("unused") // Forge instantiates this class through @SidedProxy on the client.
public final class ClientProxy extends CommonProxy {
    private final CrankPlayback playback = new CrankPlayback();

    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(playback);
        MinecraftForge.EVENT_BUS.register(new ClientNotifications());
        new HotkeyConfig(playback).register();
    }

    @Override
    public void receivePlaybackUpdate(CrankRecords.PlaybackSnapshot snapshot) {
        Minecraft.getMinecraft().addScheduledTask(() -> playback.applyPlaybackUpdate(snapshot));
    }

    @Override
    public void receiveNotification(String text) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientNotifications.show(text));
    }
}
