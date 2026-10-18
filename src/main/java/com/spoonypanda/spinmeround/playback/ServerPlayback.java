package com.spoonypanda.spinmeround.playback;

import com.spoonypanda.spinmeround.CompactDisc;
import com.spoonypanda.spinmeround.Config;
import com.spoonypanda.spinmeround.SpinMeRound;
import com.spoonypanda.spinmeround.compat.CrankCompatibility;
import com.spoonypanda.spinmeround.network.PlaybackNetwork;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = SpinMeRound.MOD_ID)
public final class ServerPlayback {
    private static final String DATA_KEY = "spinmeround";
    private static final Map<ServerLevel, Map<BlockPos, State>> LEVELS = new HashMap<>();
    private static long nextRevision;

    private static State track(ServerLevel level, BlockEntity entity) {
        if (entity == null || CrankCompatibility.kind(entity.getBlockState()) == null) return null;
        var states = LEVELS.computeIfAbsent(level, key -> new HashMap<>());
        State state = states.get(entity.getBlockPos());
        if (state == null || state.entity != entity) {
            state = new State(entity);
            states.put(entity.getBlockPos().immutable(), state);
        }
        return state;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            level.getServer().execute(() -> {
                if (level.hasChunk(chunk.getPos().x, chunk.getPos().z)) {
                    chunk.getBlockEntities().values().forEach(entity -> track(level, entity));
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BlockPos pos = event.getPos().immutable();
            level.getServer().execute(() -> { if (level.hasChunkAt(pos)) track(level, level.getBlockEntity(pos)); });
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            // Persist the latest playhead before the chunk is saved, not on the next level tick.
            var states = LEVELS.get(level);
            if (states != null) {
                for (var pos : chunk.getBlockEntities().keySet()) {
                    State state = states.remove(pos);
                    if (state != null) state.save();
                }
            }
        }
    }

    // Create's NORMAL-priority ItemUseOverrides invokes the block's use method and cancels
    // the event itself. Handle song items first, before that handler can turn the crank.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (CrankCompatibility.kind(level.getBlockState(event.getPos())) == null) return;
        var stack = event.getItemStack();
        var itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        boolean clear = !stack.isEmpty() && itemId != null && itemId.toString().equals(Config.CLEAR_ITEM.get());
        boolean disc = stack.getItem() instanceof CompactDisc || stack.getItem() instanceof RecordItem;
        // Let the original mod turn its crank. Music follows its actual state on the server tick.
        if (!clear && !disc) {
            if (level instanceof ServerLevel server) track(server, server.getBlockEntity(event.getPos()));
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(level instanceof ServerLevel server)) return;
        State state = track(server, server.getBlockEntity(event.getPos()));
        if (state == null) return;
        SongSelection selection = clear ? SongSelection.SILENT : SongSelection.fromStack(stack);
        if (!clear && selection.sound().isEmpty()) {
            event.getEntity().displayClientMessage(Component.translatable("message.spinmeround.invalid_disc"), true);
            return;
        }
        var data = state.entity.getPersistentData().getCompound(DATA_KEY);
        data.putBoolean("selected", true);
        data.putString("sound", selection.sound());
        data.putDouble("bpm", selection.bpm());
        state.entity.getPersistentData().put(DATA_KEY, data);
        state.refreshSelection();
        state.entity.setChanged();
        broadcast(server, state);
        event.getEntity().displayClientMessage(Component.translatable(clear ? "message.spinmeround.cleared" : "message.spinmeround.assigned"), true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var states = LEVELS.get(level);
        if (states == null) return;
        var iterator = states.values().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next();
            BlockPos pos = state.entity.getBlockPos();
            if (!level.hasChunkAt(pos) || state.entity.isRemoved() || level.getBlockEntity(pos) != state.entity) {
                iterator.remove();
                continue;
            }
            boolean changed = state.refreshSelection();
            boolean turning = state.turning();
            float previous = state.clock.gain();
            state.clock.tick(turning && !state.song.sound().isEmpty(), Config.GRACE_TICKS.get(), Config.FADE_TICKS.get());
            if (state.clock.gain() > 0 || previous > 0) {
                var data = state.entity.getPersistentData().getCompound(DATA_KEY);
                data.putLong("ticks", state.clock.ticks());
                data.putString("lastSound", state.song.sound());
                state.entity.getPersistentData().put(DATA_KEY, data);
                if (level.getGameTime() % 100 == 0 || state.clock.gain() == 0) state.entity.setChanged();
            }
            if (changed || previous != state.clock.gain() || (!state.song.sound().isEmpty() && level.getGameTime() % 20 == 0)) {
                broadcast(level, state);
            }
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var states = LEVELS.remove(level);
            if (states != null) states.values().forEach(State::save);
        }
    }

    private static void broadcast(ServerLevel level, State state) {
        var pos = state.entity.getBlockPos();
        PlaybackNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 96, level.dimension())),
                new PlaybackNetwork.Snapshot(level.dimension().location(), pos, state.song.sound(), state.song.bpm(),
                        state.clock.ticks(), level.getGameTime(), state.clock.gain(), state.revision,
                        Config.LOOP.get(), Config.VOLUME.get()));
    }

    private static final class State {
        final BlockEntity entity;
        final CrankCompatibility.Kind kind;
        SongSelection song;
        PlaybackClock clock;
        long revision;
        int generatorStep;
        boolean failed;

        State(BlockEntity entity) {
            this.entity = entity;
            kind = CrankCompatibility.kind(entity.getBlockState());
            if (kind == CrankCompatibility.Kind.GENERATOR) generatorStep = CrankCompatibility.generatorStep(entity.getBlockState());
            var data = entity.getPersistentData().getCompound(DATA_KEY);
            song = resolve();
            clock = new PlaybackClock(song.sound().equals(data.getString("lastSound")) ? data.getLong("ticks") : 0);
            // A replacement block at the same position must reset the old client's source too.
            revision = ++nextRevision;
        }

        SongSelection resolve() {
            var data = entity.getPersistentData().getCompound(DATA_KEY);
            return data.getBoolean("selected") ? new SongSelection(data.getString("sound"), data.getDouble("bpm")) : SongSelection.defaultSong();
        }

        boolean refreshSelection() {
            SongSelection current = resolve();
            if (current.equals(song)) return false;
            song = current;
            clock = new PlaybackClock(0);
            revision = ++nextRevision;
            var data = entity.getPersistentData().getCompound(DATA_KEY);
            data.putString("lastSound", song.sound());
            data.putLong("ticks", 0);
            data.putLong("revision", revision);
            entity.getPersistentData().put(DATA_KEY, data);
            entity.setChanged();
            return true;
        }

        boolean turning() {
            if (failed) return false;
            try {
                if (kind != CrankCompatibility.Kind.GENERATOR) return CrankCompatibility.isTurning(entity);
                int next = CrankCompatibility.generatorStep(entity.getBlockState());
                boolean changed = next != generatorStep;
                generatorStep = next;
                return changed;
            } catch (RuntimeException exception) {
                failed = true;
                SpinMeRound.LOGGER.error("Disabling crank activity at {} after integration failure", entity.getBlockPos(), exception);
                return false;
            }
        }

        void save() {
            var data = entity.getPersistentData().getCompound(DATA_KEY);
            data.putLong("ticks", clock.ticks());
            data.putString("lastSound", song.sound());
            entity.getPersistentData().put(DATA_KEY, data);
            entity.setChanged();
        }
    }
}
