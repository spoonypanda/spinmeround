package com.spoonypanda.spinmeround.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.spoonypanda.spinmeround.Config;
import com.spoonypanda.spinmeround.SpinMeRound;
import com.spoonypanda.spinmeround.compat.CrankCompatibility;
import java.lang.reflect.Field;
import com.spoonypanda.spinmeround.network.PlaybackNetwork;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SpinMeRound.MOD_ID, value = Dist.CLIENT)
public final class ClientPlayback {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static final Set<String> REPORTED = new HashSet<>();
    private static ClientLevel world;
    private static boolean muted;
    private static float musicGain = 1;
    private static final KeyMapping MUTE = new KeyMapping("key.spinmeround.mute", KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.spinmeround");

    @Mod.EventBusSubscriber(modid = SpinMeRound.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> PlaybackNetwork.receiver = ClientPlayback::receive);
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) { event.register(MUTE); }
    }

    private static void reset() {
        ENTRIES.values().forEach(Entry::stop);
        ENTRIES.clear();
        musicGain = 1;
        applyMusicGain();
        REPORTED.clear();
        world = MC.level;
    }

    private static void receive(PlaybackNetwork.Snapshot snapshot) {
        if (world != MC.level) reset();
        if (world == null || !world.dimension().location().equals(snapshot.dimension())) return;
        Entry entry = ENTRIES.computeIfAbsent(snapshot.pos(), pos -> new Entry());
        if (entry.snapshot != null && (!entry.snapshot.sound().equals(snapshot.sound())
                || entry.snapshot.revision() != snapshot.revision() || entry.snapshot.loop() != snapshot.loop())) {
            entry.stop();
        }
        entry.snapshot = snapshot;
        entry.received = world.getGameTime();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (world != MC.level) reset();
        if (world == null || MC.player == null) return;
        while (MUTE.consumeClick()) {
            muted = !muted;
            MC.player.displayClientMessage(Component.translatable(muted ? "message.spinmeround.muted" : "message.spinmeround.unmuted"), true);
        }
        if (MC.isPaused()) return;
        int active = 0;
        boolean audible = false;
        var iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            Entry entry = item.getValue();
            var snapshot = entry.snapshot;
            var pos = item.getKey();
            if (world.getGameTime() - entry.received > 60 || !world.hasChunkAt(pos)
                    || CrankCompatibility.kind(world.getBlockState(pos)) == null) {
                entry.stop();
                iterator.remove();
                continue;
            }
            boolean nearby = MC.player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) < 64 * 64;
            if (snapshot.gain() <= 0 || snapshot.sound().isEmpty() || !nearby || active >= 32) {
                entry.stop();
                continue;
            }
            active++;
            emitNoteOnBeat(entry, pos);
            if (entry.sound == null) {
                var id = ResourceLocation.tryParse(snapshot.sound());
                if (id == null) continue;
                if (MC.getSoundManager().getSoundEvent(id) == null) {
                    warnOnce("Missing crank sound event: " + id, null);
                    continue;
                }
                entry.sound = new CrankSound(entry, pos, id);
                MC.getSoundManager().play(entry.sound);
            }
            if (!muted && snapshot.volume() > 0 && MC.options.getSoundSourceVolume(SoundSource.RECORDS) > 0
                    && MC.options.getSoundSourceVolume(SoundSource.MASTER) > 0 && MC.getSoundManager().isActive(entry.sound)) {
                audible = true;
            }
        }
        float target = Config.FADE_BACKGROUND_MUSIC.get() && audible ? 0 : 1;
        musicGain += Math.max(-.1F, Math.min(.1F, target - musicGain));
        applyMusicGain();
    }

    /** Visual beats follow the shared timeline, including when this listener's audio is muted or unavailable. */
    private static void emitNoteOnBeat(Entry entry, BlockPos pos) {
        var snapshot = entry.snapshot;
        long beat = (long) Math.floor(entry.estimatedTicks() * (snapshot.bpm() > 0 ? snapshot.bpm() / 1200 : .1));
        if (beat != entry.lastBeat) CrankNoteParticles.spawn(MC, world, pos);
        entry.lastBeat = beat;
    }

    @SubscribeEvent
    public static void onSoundReload(SoundEngineLoadEvent event) { MC.execute(ClientPlayback::reset); }

    private static boolean musicAccessFailed;
    private static Field engineField;
    private static Field channelsField;

    /** Retain vanilla sound identity and scheduling; change only the actual music channels. */
    private static void applyMusicGain() {
        if (musicAccessFailed) return;
        try {
            if (engineField == null) {
                engineField = ObfuscationReflectionHelper.findField(SoundManager.class, "f_120349_");
                channelsField = ObfuscationReflectionHelper.findField(SoundEngine.class, "f_120226_");
            }
            SoundEngine engine = (SoundEngine) engineField.get(MC.getSoundManager());
            @SuppressWarnings("unchecked")
            Map<SoundInstance, ChannelAccess.ChannelHandle> channels =
                    (Map<SoundInstance, ChannelAccess.ChannelHandle>) channelsField.get(engine);
            for (var item : channels.entrySet()) {
                SoundInstance sound = item.getKey();
                if (sound.getSource() == SoundSource.MUSIC) {
                    float volume = Math.max(0, Math.min(1, sound.getVolume() * MC.options.getSoundSourceVolume(SoundSource.MUSIC))) * musicGain;
                    item.getValue().execute(channel -> channel.setVolume(volume));
                }
            }
        } catch (IllegalAccessException | RuntimeException exception) {
            musicAccessFailed = true;
            warnOnce("Background music fading is unavailable; crank playback remains enabled", exception);
        }
    }

    private static void warnOnce(String message, Throwable error) {
        if (REPORTED.add(message)) SpinMeRound.LOGGER.warn(message, error);
    }

    private static final class Entry {
        PlaybackNetwork.Snapshot snapshot;
        long received;
        long lastBeat = -1;
        CrankSound sound;

        long estimatedTicks() {
            return Math.max(0, snapshot.ticks()) + (snapshot.gain() > 0
                    ? Math.max(0, Math.min(40, world.getGameTime() - snapshot.worldTick())) : 0);
        }

        void stop() {
            if (sound != null) MC.getSoundManager().stop(sound);
            sound = null;
        }
    }

    private static final class CrankSound extends AbstractTickableSoundInstance {
        private final Entry entry;
        private final long startTicks;
        private final boolean repeat;

        CrankSound(Entry entry, BlockPos pos, ResourceLocation id) {
            super(SoundEvent.createVariableRangeEvent(id), SoundSource.RECORDS, RandomSource.create());
            this.entry = entry;
            startTicks = entry.estimatedTicks();
            repeat = entry.snapshot.loop();
            x = pos.getX() + .5;
            y = pos.getY() + .5;
            z = pos.getZ() + .5;
            // The decoder handles looping so the initial seek is applied only once.
            looping = false;
            tick();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            var events = super.resolve(manager);
            if (sound != SoundManager.EMPTY_SOUND) {
                sound = new Sound(sound.getLocation().toString(), sound.getVolume(), sound.getPitch(), sound.getWeight(),
                        sound.getType(), true, false, 64);
            }
            return events;
        }

        @Override
        public float getPitch() { return 1; }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {
            volume = muted ? 0 : entry.snapshot.gain() * entry.snapshot.volume() / 100F;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound asset, boolean looping) {
            var resources = MC.getResourceManager();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return new SeekableAudioStream(resources, asset.getPath(), startTicks, repeat);
                } catch (IOException | RuntimeException exception) {
                    // This callback runs on an audio worker, so marshal the shared error set to the client.
                    MC.execute(() -> warnOnce("Cannot stream crank sound: " + getLocation(), exception));
                    throw new CompletionException(exception);
                }
            }, Util.backgroundExecutor());
        }
    }

}
