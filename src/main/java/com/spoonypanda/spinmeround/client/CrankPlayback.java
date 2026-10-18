package com.spoonypanda.spinmeround.client;

import com.spoonypanda.spinmeround.Config;
import com.spoonypanda.spinmeround.CrankRecords;
import com.spoonypanda.spinmeround.SpinMeRound;
import com.spoonypanda.spinmeround.playback.ManualMillAccess;
import com.spoonypanda.spinmeround.playback.PlaybackClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Owns sources outside SoundManager's playing-sound list so paused streams stay alive. */
public final class CrankPlayback {
    private static final int HEARING_RADIUS_BLOCKS = 64;
    private static final int SNAPSHOT_REQUEST_RADIUS_BLOCKS = 120;
    private static final int SNAPSHOT_RETRY_INTERVAL_TICKS = 100;
    private static final int MAX_ACTIVE_PLAYBACKS = 32;
    private static final int MAX_EXTRAPOLATION_TICKS = 40;
    private static final int RESYNC_WARMUP_TICKS = 40;
    private static final long RESYNC_TOLERANCE_MILLIS = 1500;
    private static final long MILLIS_PER_TICK = 50;
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Map<BlockPos, ClientPlayback> playbacksByCrank = new HashMap<>();
    private final Map<BlockPos, CrankRecords.PlaybackSnapshot> snapshotsByCrank = new HashMap<>();
    private final Set<String> reportedErrors = new HashSet<>();
    private WorldClient playbackWorld;
    private SoundSystem soundSystem;
    private SoundManager soundManager;
    private Method crankUpMethod;
    private int clientTicks;
    private int nextSourceId;
    private float backgroundMusicGain = 1;
    private boolean crankMusicMuted;

    public CrankPlayback() {
        try {
            SoundSystemConfig.setCodec("smr", SeekableVorbisCodec.class);
        } catch (SoundSystemException exception) {
            throw new IllegalStateException("Cannot register crank audio codec", exception);
        }
    }

    /** Applies a server snapshot; this method does not handle right-click interactions. */
    public void applyPlaybackUpdate(CrankRecords.PlaybackSnapshot snapshot) {
        // The client tick resets playback state when the WorldClient instance changes.
        if (minecraft.world == null || minecraft.world != playbackWorld
                || minecraft.world.provider.getDimension() != snapshot.dimensionId) {
            return;
        }
        BlockPos crankPosition = BlockPos.fromLong(snapshot.packedCrankPosition);
        CrankRecords.PlaybackSnapshot previousSnapshot = snapshotsByCrank.put(crankPosition, snapshot);
        ClientPlayback playback = playbacksByCrank.get(crankPosition);
        boolean songSelectionChanged = previousSnapshot == null || !snapshot.soundEventId.equals(previousSnapshot.soundEventId)
                || snapshot.selectionRevision != previousSnapshot.selectionRevision || snapshot.bpm != previousSnapshot.bpm;
        boolean needsPlaybackResync = playback != null && playback.initialSeekCommitted.get() && playback.playRequested
                && soundSystem != null && soundSystem.playing(playback.sourceName)
                && clientTicks - playback.createdAtClientTick > RESYNC_WARMUP_TICKS
                && Math.abs(playback.audioPlayheadMillis() - estimatePlayheadTicks(snapshot) * MILLIS_PER_TICK)
                > RESYNC_TOLERANCE_MILLIS;
        if (playback != null && (songSelectionChanged || needsPlaybackResync)) {
            playbacksByCrank.remove(crankPosition);
            playback.remove();
        }
    }

    private long estimatePlayheadTicks(CrankRecords.PlaybackSnapshot snapshot) {
        long ticksSinceSnapshot = playbackWorld.getTotalWorldTime() - snapshot.serverWorldTick;
        long advanceTicks = snapshot.fadeGain > 0
                ? Math.max(0, Math.min(MAX_EXTRAPOLATION_TICKS, ticksSinceSnapshot)) : 0;
        return Math.max(0, snapshot.playheadTicks) + advanceTicks;
    }

    public void toggleCrankMusicMute() {
        crankMusicMuted = !crankMusicMuted;
        if (minecraft.player != null) {
            String message = crankMusicMuted ? "message.spinmeround.muted" : "message.spinmeround.unmuted";
            ClientNotifications.show(new TextComponentTranslation(message).getFormattedText());
        }
    }

    @SuppressWarnings("unused") // Forge invokes this handler through its event bus.
    @SubscribeEvent
    public void onSoundReload(SoundLoadEvent event) {
        minecraft.addScheduledTask(() -> {
            playbacksByCrank.clear();
            snapshotsByCrank.clear();
            soundSystem = null;
            backgroundMusicGain = 1;
            reportedErrors.clear();
        });
    }

    @SuppressWarnings("unused") // Forge invokes this handler through its event bus.
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (playbackWorld != minecraft.world) {
            clearPlaybackState();
            playbackWorld = minecraft.world;
        }
        if (playbackWorld == null || minecraft.player == null) {
            return;
        }
        clientTicks++;
        try {
            if (!refreshSoundSystem()) {
                return;
            }
            updateCrankPlaybacks();
            updateBackgroundMusicFade();
        } catch (Exception exception) {
            logOnce("Crank playback integration failed", exception);
            clearPlaybackState();
        }
    }

    private boolean refreshSoundSystem() {
        soundManager = ObfuscationReflectionHelper.getPrivateValue(SoundHandler.class,
                minecraft.getSoundHandler(), "field_147694_f");
        SoundSystem current = ObfuscationReflectionHelper.getPrivateValue(SoundManager.class,
                soundManager, "field_148620_e");
        if (current == null) {
            return false;
        }
        if (current != soundSystem) {
            playbacksByCrank.clear();
            soundSystem = current;
        }
        return true;
    }

    private void updateCrankPlaybacks() throws ReflectiveOperationException {
        Set<BlockPos> loadedCrankPositions = new HashSet<>();
        for (TileEntity crank : playbackWorld.loadedTileEntityList) {
            if (!CrankRecords.isCrank(crank) || crank.isInvalid()) {
                continue;
            }
            BlockPos crankPosition = crank.getPos();
            loadedCrankPositions.add(crankPosition);
            boolean needsSnapshot = !snapshotsByCrank.containsKey(crankPosition)
                    || (snapshotsByCrank.get(crankPosition) == null
                    && clientTicks % SNAPSHOT_RETRY_INTERVAL_TICKS == 0);
            if (needsSnapshot && minecraft.player.getDistanceSq(crankPosition)
                    <= SNAPSHOT_REQUEST_RADIUS_BLOCKS * SNAPSHOT_REQUEST_RADIUS_BLOCKS) {
                // A null value marks a pending request, avoiding a new packet every tick.
                snapshotsByCrank.put(crankPosition, null);
                CrankRecords.NETWORK.sendToServer(new CrankRecords.PlaybackStateRequest(
                        playbackWorld.provider.getDimension(), crankPosition));
            }
            CrankRecords.PlaybackSnapshot snapshot = snapshotsByCrank.get(crankPosition);
            if (snapshot == null) {
                continue;
            }
            ClientPlayback playback = playbacksByCrank.get(crankPosition);
            if (playback == null && snapshot.fadeGain > 0 && !snapshot.soundEventId.isEmpty()
                    && minecraft.player.getDistanceSq(crankPosition) <= HEARING_RADIUS_BLOCKS * HEARING_RADIUS_BLOCKS
                    && playbacksByCrank.size() < MAX_ACTIVE_PLAYBACKS) {
                playback = createPlayback(crankPosition, snapshot);
                if (playback != null) {
                    playbacksByCrank.put(crankPosition, playback);
                }
            }
            if (playback != null) {
                playback.applySnapshot(snapshot);
                emitNoteOnBeat(crank, playback, snapshot);
            }
        }
        removeUnloadedCrankPlaybacks(loadedCrankPositions);
    }

    private void emitNoteOnBeat(TileEntity crank, ClientPlayback playback,
                               CrankRecords.PlaybackSnapshot snapshot) throws ReflectiveOperationException {
        long playheadTicks = estimatePlayheadTicks(snapshot);
        long particleBeatIndex = snapshot.bpm > 0
                ? PlaybackClock.beatIndexAt(playheadTicks, snapshot.bpm) : playheadTicks / 10;
        // Follow the music through grace and fade-out between crank turns.
        if (particleBeatIndex != playback.lastParticleBeatIndex && playback.playRequested
                && soundSystem.playing(playback.sourceName) && !minecraft.isGamePaused()) {
            BlockPos noteOrigin = crank.getPos();
            if (!ManualMillAccess.isManualMill(crank)) {
                if (crankUpMethod == null) {
                    crankUpMethod = crank.getClass().getMethod("getUp");
                }
                EnumFacing crankFacing = (EnumFacing) crankUpMethod.invoke(crank);
                noteOrigin = crank.getPos().offset(crankFacing.getOpposite());
            }
            CrankNoteParticle.spawn(minecraft, playbackWorld, noteOrigin);
        }
        playback.lastParticleBeatIndex = particleBeatIndex;
    }

    private void removeUnloadedCrankPlaybacks(Set<BlockPos> loadedCrankPositions) {
        Iterator<Map.Entry<BlockPos, ClientPlayback>> iterator = playbacksByCrank.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ClientPlayback> entry = iterator.next();
            if (!loadedCrankPositions.contains(entry.getKey())) {
                entry.getValue().remove();
                iterator.remove();
            }
        }
        snapshotsByCrank.keySet().retainAll(loadedCrankPositions);
    }

    private void updateBackgroundMusicFade() {
        boolean nearbyCrankAudible = false;
        for (Map.Entry<BlockPos, ClientPlayback> entry : playbacksByCrank.entrySet()) {
            ClientPlayback playback = entry.getValue();
            if (!crankMusicMuted && playback.playRequested && playback.assetVolumeMultiplier > 0
                    && Config.volumeMultiplier > 0
                    && minecraft.gameSettings.getSoundLevel(SoundCategory.RECORDS) > 0
                    && minecraft.player.getDistanceSq(entry.getKey()) < HEARING_RADIUS_BLOCKS * HEARING_RADIUS_BLOCKS
                    && soundSystem.playing(playback.sourceName)) {
                nearbyCrankAudible = true;
                break;
            }
        }
        backgroundMusicGain = Config.fadeBackgroundMusic && nearbyCrankAudible
                ? Math.max(0, backgroundMusicGain - 0.1F) : Math.min(1, backgroundMusicGain + 0.1F);
        applyBackgroundMusicVolume();
    }

    private ClientPlayback createPlayback(BlockPos crankPosition, CrankRecords.PlaybackSnapshot snapshot) {
        try {
            ResourceLocation soundEventLocation = new ResourceLocation(snapshot.soundEventId);
            SoundEventAccessor accessor = minecraft.getSoundHandler().getAccessor(soundEventLocation);
            Sound assetSound = accessor == null ? SoundHandler.MISSING_SOUND : accessor.cloneEntry();
            if (assetSound == SoundHandler.MISSING_SOUND) {
                logOnce("Missing crank sound: " + soundEventLocation, null);
                return null;
            }
            ResourceLocation audioResourceId = assetSound.getSoundAsOggLocation();
            String sourceName = "spinmeround_" + nextSourceId++;
            ClientPlayback playback = new ClientPlayback(sourceName, assetSound.getVolume());
            playback.requestedSeekMillis.set(estimatePlayheadTicks(snapshot) * MILLIS_PER_TICK);
            URL audioUrl = new URL(null, "smr:" + audioResourceId + "/" + sourceName, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL address) {
                    return new SeekableVorbisCodec.SeekableAudioConnection(address) {
                        @Override
                        public void connect() { }

                        @Override
                        public long requestedOffsetMillis() {
                            // Only the first pass seeks; later loops start at the beginning.
                            return playback.initialSeekCommitted.get() ? 0 : playback.requestedSeekMillis.get();
                        }

                        @Override
                        public boolean shouldLoop() {
                            return Config.loopPlayback;
                        }

                        @Override
                        public void commitInitialSeek(long requestedOffsetMillis) {
                            if (playback.initialSeekCommitted.compareAndSet(false, true)) {
                                playback.seekOffsetMillis.set(requestedOffsetMillis);
                            }
                        }

                        @Override
                        public InputStream getInputStream() throws IOException {
                            return new BufferedInputStream(minecraft.getResourceManager().getResource(audioResourceId).getInputStream(), 65536);
                        }
                    };
                }
            });
            // Verify the resource can be read before handing it to the audio thread.
            try (InputStream audioStream = minecraft.getResourceManager().getResource(audioResourceId).getInputStream()) {
                if (audioStream.read() == -1) {
                    throw new IOException("Crank audio resource is empty: " + audioResourceId);
                }
            }
            soundSystem.newStreamingSource(false, sourceName, audioUrl, sourceName + ".smr", Config.loopPlayback,
                    crankPosition.getX() + 0.5F, crankPosition.getY() + 0.5F, crankPosition.getZ() + 0.5F,
                    SoundSystemConfig.ATTENUATION_LINEAR, HEARING_RADIUS_BLOCKS);
            // Shared music uses a fixed pitch so all listeners advance at the same rate.
            soundSystem.setPitch(sourceName, 1);
            soundSystem.setVolume(sourceName, 0);
            return playback;
        } catch (Exception exception) {
            logOnce("Cannot load crank sound: " + snapshot.soundEventId, exception);
            return null;
        }
    }

    private void logOnce(String message, Exception exception) {
        if (reportedErrors.add(message)) {
            SpinMeRound.logger.warn(message, exception);
        }
    }

    private void clearPlaybackState() {
        backgroundMusicGain = 1;
        if (soundSystem != null && soundManager != null) {
            try {
                applyBackgroundMusicVolume();
            } catch (RuntimeException exception) {
                logOnce("Cannot restore background music volume", exception);
            }
        }
        if (soundSystem != null) {
            for (ClientPlayback playback : playbacksByCrank.values()) {
                playback.remove();
            }
        }
        playbacksByCrank.clear();
        snapshotsByCrank.clear();
        soundSystem = null;
    }

    private void applyBackgroundMusicVolume() {
        Map<String, ISound> playingSounds = ObfuscationReflectionHelper.getPrivateValue(SoundManager.class,
                soundManager, "field_148629_h");
        for (Map.Entry<String, ISound> entry : playingSounds.entrySet()) {
            ISound assetSound = entry.getValue();
            if (assetSound.getCategory() == SoundCategory.MUSIC) {
                soundSystem.setVolume(entry.getKey(), backgroundMusicGain
                        * Math.max(0, Math.min(1, assetSound.getVolume()
                        * minecraft.gameSettings.getSoundLevel(SoundCategory.MUSIC))));
            }
        }
    }
    private final class ClientPlayback {
        private final String sourceName;
        private final float assetVolumeMultiplier;
        private final int createdAtClientTick = clientTicks;
        // The streaming decoder commits its initial seek from the audio thread.
        private final AtomicBoolean initialSeekCommitted = new AtomicBoolean();
        private final AtomicLong requestedSeekMillis = new AtomicLong();
        private final AtomicLong seekOffsetMillis = new AtomicLong();
        private boolean playRequested;
        private long lastParticleBeatIndex = -1;

        private ClientPlayback(String sourceName, float assetVolumeMultiplier) {
            this.sourceName = sourceName;
            this.assetVolumeMultiplier = assetVolumeMultiplier;
        }

        private long audioPlayheadMillis() {
            return seekOffsetMillis.get() + (long) Math.max(0, soundSystem.millisecondsPlayed(sourceName));
        }

        private void applySnapshot(CrankRecords.PlaybackSnapshot snapshot) {
            requestedSeekMillis.set(estimatePlayheadTicks(snapshot) * MILLIS_PER_TICK);
            float playbackVolume = snapshot.fadeGain * Math.min(1, assetVolumeMultiplier * Config.volumeMultiplier
                    * minecraft.gameSettings.getSoundLevel(SoundCategory.RECORDS));
            // Muting affects volume only, so the shared playhead keeps advancing.
            soundSystem.setVolume(sourceName, crankMusicMuted ? 0 : playbackVolume);
            boolean shouldPlay = snapshot.fadeGain > 0 && !minecraft.isGamePaused();
            if (shouldPlay != playRequested) {
                if (shouldPlay) {
                    soundSystem.play(sourceName);
                } else {
                    soundSystem.pause(sourceName);
                }
                playRequested = shouldPlay;
            }
        }

        private void remove() {
            soundSystem.stop(sourceName);
            soundSystem.removeSource(sourceName);
        }
    }
}
