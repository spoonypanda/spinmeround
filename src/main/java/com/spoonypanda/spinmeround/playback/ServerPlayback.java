package com.spoonypanda.spinmeround.playback;

import com.spoonypanda.spinmeround.Config;
import com.spoonypanda.spinmeround.CrankRecords;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ServerPlayback {
    public static final ServerPlayback INSTANCE = new ServerPlayback();
    private static final String PLAYHEAD_TICKS_KEY = "spinmeround_ticks";
    private static final String LAST_SOUND_KEY = "spinmeround_last_sound";
    private static final String SELECTION_REVISION_KEY = "spinmeround_revision";
    private static final int SAVE_INTERVAL_TICKS = 100;
    private static final int SNAPSHOT_INTERVAL_TICKS = 20;
    private final Map<WorldServer, Map<BlockPos, CrankState>> playbackByWorld = new HashMap<>();

    /** Sends one snapshot in response to a listener's request. */
    public void sendPlaybackSnapshot(EntityPlayerMP player, BlockPos position) {
        CrankState state = getOrCreateCrankState(player.getServerWorld(), position);
        if (state != null) {
            CrankRecords.NETWORK.sendTo(state.createSnapshot(), player);
        }
    }

    public void onSongSelectionChanged(WorldServer world, BlockPos position) {
        CrankState state = getOrCreateCrankState(world, position);
        if (state != null) {
            state.refreshSongSelection();
            broadcastPlaybackSnapshot(state);
        }
    }

    private CrankState getOrCreateCrankState(WorldServer world, BlockPos position) {
        TileEntity crank = world.getTileEntity(position);
        if (!CrankRecords.isCrank(crank)) {
            return null;
        }
        Map<BlockPos, CrankState> entries = playbackByWorld.computeIfAbsent(world, key -> new HashMap<>());
        CrankState state = entries.get(position);
        if (state == null || state.crank != crank) {
            state = new CrankState(world, crank);
            entries.put(position.toImmutable(), state);
        }
        return state;
    }

    private static void broadcastPlaybackSnapshot(CrankState state) {
        BlockPos position = state.crank.getPos();
        CrankRecords.NETWORK.sendToAllAround(state.createSnapshot(), new NetworkRegistry.TargetPoint(
                state.world.provider.getDimension(), position.getX(), position.getY(), position.getZ(),
                CrankRecords.SYNC_RADIUS_BLOCKS));
    }

    @SuppressWarnings("unused") // Forge invokes this handler through its event bus.
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        Map<BlockPos, CrankState> entries = playbackByWorld.get(world);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        long worldTick = world.getTotalWorldTime();
        boolean saveDue = worldTick % SAVE_INTERVAL_TICKS == 0;
        boolean snapshotDue = worldTick % SNAPSHOT_INTERVAL_TICKS == 0;
        Iterator<CrankState> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            CrankState state = iterator.next();
            if (!world.isBlockLoaded(state.crank.getPos()) || state.crank.isInvalid()
                    || world.getTileEntity(state.crank.getPos()) != state.crank) {
                iterator.remove();
                continue;
            }
            boolean songChanged = state.refreshSongSelection();
            boolean turning = !state.songSelection.soundEventId.isEmpty() && CrankAccess.isTurning(state.crank);
            float previousGain = state.playbackClock.fadeGain();
            if (ManualMillAccess.isManualMill(state.crank)) {
                state.playbackClock.tick(turning, Config.graceTicks, Config.fadeTicks,
                        CrankAccess.playbackGainLimit(state.crank));
            } else {
                state.playbackClock.tick(turning, Config.graceTicks, Config.fadeTicks);
            }
            float currentGain = state.playbackClock.fadeGain();
            if (currentGain > 0) {
                state.crank.getTileData().setLong(PLAYHEAD_TICKS_KEY, state.playbackClock.playheadTicks());
                if (saveDue) {
                    state.crank.markDirty();
                }
            }
            if (previousGain > 0 && currentGain == 0) {
                state.crank.markDirty();
            }
            if (songChanged || previousGain != currentGain || turning != state.turning
                    || (currentGain > 0 && snapshotDue)) {
                state.turning = turning;
                broadcastPlaybackSnapshot(state);
            }
        }
    }

    @SuppressWarnings("unused") // Forge invokes this handler through its event bus.
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof WorldServer) {
            playbackByWorld.remove((WorldServer) event.getWorld());
        }
    }
    private static final class CrankState {
        private final WorldServer world;
        private final TileEntity crank;
        private SongSelection songSelection;
        private PlaybackClock playbackClock;
        private boolean turning;
        private long selectionRevision;
        private String cachedStoredSelection;
        private double cachedStoredBpm;
        private String cachedDefaultRecordDefinition;
        private String cachedDefaultSoundEventId;

        private CrankState(WorldServer world, TileEntity crank) {
            this.world = world;
            this.crank = crank;
            songSelection = resolveSong();
            NBTTagCompound data = crank.getTileData();
            long restoredPlayheadTicks = songSelection.soundEventId.equals(data.getString(LAST_SOUND_KEY)) ? data.getLong(PLAYHEAD_TICKS_KEY) : 0;
            playbackClock = new PlaybackClock(restoredPlayheadTicks);
            selectionRevision = data.getLong(SELECTION_REVISION_KEY);
            data.setString(LAST_SOUND_KEY, songSelection.soundEventId);
        }

        private SongSelection resolveSong() {
            NBTTagCompound data = crank.getTileData();
            String storedSelection = data.getString(CrankRecords.RECORD_KEY);
            double storedBpm = data.getDouble(CrankRecords.BPM_KEY);
            // Parse item NBT and resolve registry entries only when selection inputs change.
            if (songSelection != null && storedSelection.equals(cachedStoredSelection)
                    && Double.compare(storedBpm, cachedStoredBpm) == 0
                    && Config.defaultRecordDefinition.equals(cachedDefaultRecordDefinition)
                    && Config.defaultSoundEventId.equals(cachedDefaultSoundEventId)) {
                return songSelection;
            }
            cachedStoredSelection = storedSelection;
            cachedStoredBpm = storedBpm;
            cachedDefaultRecordDefinition = Config.defaultRecordDefinition;
            cachedDefaultSoundEventId = Config.defaultSoundEventId;
            return SongSelection.resolveSong(storedSelection, storedBpm);
        }

        private boolean refreshSongSelection() {
            SongSelection current = resolveSong();
            if (current.equals(songSelection)) {
                return false;
            }
            songSelection = current;
            turning = false;
            playbackClock = new PlaybackClock(0);
            selectionRevision++;
            NBTTagCompound data = crank.getTileData();
            data.setLong(PLAYHEAD_TICKS_KEY, 0);
            data.setLong(SELECTION_REVISION_KEY, selectionRevision);
            data.setString(LAST_SOUND_KEY, songSelection.soundEventId);
            crank.markDirty();
            return true;
        }

        private CrankRecords.PlaybackSnapshot createSnapshot() {
            return new CrankRecords.PlaybackSnapshot(world.provider.getDimension(), crank.getPos(), songSelection.soundEventId,
                    songSelection.bpm, playbackClock.playheadTicks(), world.getTotalWorldTime(), playbackClock.fadeGain(), turning, selectionRevision);
        }
    }
}
