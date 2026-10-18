package com.spoonypanda.spinmeround.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** Client controls registered in Minecraft's Controls menu. */
public final class HotkeyConfig {
    private final CrankPlayback playback;
    private final KeyBinding muteCrankMusic = new KeyBinding("key.spinmeround.mute_crank_music",
            KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Keyboard.KEY_M, "key.categories.spinmeround");

    public HotkeyConfig(CrankPlayback playback) {
        this.playback = playback;
    }

    public void register() {
        ClientRegistry.registerKeyBinding(muteCrankMusic);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SuppressWarnings("unused") // Forge invokes this handler through its event bus.
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (muteCrankMusic.isPressed()) {
            playback.toggleCrankMusicMute();
        }
    }
}
