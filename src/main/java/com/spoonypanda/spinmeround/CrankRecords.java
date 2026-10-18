package com.spoonypanda.spinmeround;

import com.spoonypanda.spinmeround.playback.ManualMillAccess;
import com.spoonypanda.spinmeround.playback.ServerPlayback;
import com.spoonypanda.spinmeround.playback.SongSelection;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemRecord;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Handles song assignment and the playback requests/snapshots shared with listeners. */
public final class CrankRecords {
    public static final String CLEARED_SELECTION = "silent";
    public static final String RECORD_KEY = "spinmeround_record";
    public static final String BPM_KEY = "spinmeround_bpm";
    public static final int SYNC_RADIUS_BLOCKS = 128;
    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(SpinMeRound.MOD_ID);

    public static void init() {
        NETWORK.registerMessage(PlaybackStateRequestHandler.class, PlaybackStateRequest.class, 0, Side.SERVER);
        NETWORK.registerMessage(PlaybackSnapshotHandler.class, PlaybackSnapshot.class, 1, Side.CLIENT);
        NETWORK.registerMessage(PlaybackNotificationHandler.class, PlaybackNotification.class, 2, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(new CrankRecords());
        MinecraftForge.EVENT_BUS.register(ServerPlayback.INSTANCE);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted") // Guard clauses reject unsupported tiles early.
    public static boolean isCrank(TileEntity tile) {
        if (tile == null) {
            return false;
        }
        if (ManualMillAccess.isManualMill(tile)) {
            return true;
        }
        for (Class<?> type = tile.getClass(); type != null; type = type.getSuperclass()) {
            if ("appeng.tile.grindstone.TileCrank".equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onCrankRightClick(PlayerInteractEvent.RightClickBlock event) {
        ResourceLocation itemId = event.getItemStack().getItem().getRegistryName();
        boolean isCompactDisc = event.getItemStack().getItem() == CompactDisc.INSTANCE;
        boolean isClearItem = !event.getItemStack().isEmpty() && Config.clearItemId.equals(
                String.valueOf(itemId));
        if (!isClearItem && !isCompactDisc && !(event.getItemStack().getItem() instanceof ItemRecord)) {
            return;
        }
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        if (!isCrank(tile)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (event.getWorld().isRemote) {
            return;
        }
        String soundEventId = isCompactDisc ? CompactDisc.getSoundEventId(event.getItemStack()) : "";
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        if (!isClearItem && isCompactDisc && soundEventId.isEmpty()) {
            sendNotification(player, "This disc needs a valid Sound NBT tag, for example mymusic:crank_song.");
            return;
        }
        if (itemId == null) {
            return;
        }
        String storedSelection;
        if (isClearItem) {
            storedSelection = CLEARED_SELECTION;
        } else if (isCompactDisc) {
            storedSelection = CompactDisc.CUSTOM_SOUND_PREFIX + soundEventId;
        } else {
            storedSelection = itemId.toString();
        }
        tile.getTileData().setString(RECORD_KEY, storedSelection);
        tile.getTileData().setDouble(BPM_KEY, isClearItem ? 0 : SongSelection.readBpm(event.getItemStack()));
        tile.markDirty();
        ServerPlayback.INSTANCE.onSongSelectionChanged((WorldServer) event.getWorld(), event.getPos());
        sendNotification(player, isClearItem ? "Crank song cleared." : "Crank record: " + event.getItemStack().getDisplayName());
    }

    private static void sendNotification(EntityPlayerMP player, String text) {
        NETWORK.sendTo(new PlaybackNotification(text), player);
    }

    /** Complete server playback state for one crank, including its timing and fade. */
    public static final class PlaybackSnapshot implements IMessage {
        public int dimensionId;
        public long packedCrankPosition;
        public String soundEventId;
        public double bpm;
        public long playheadTicks;
        public long serverWorldTick;
        public float fadeGain;
        public boolean turning;
        public long selectionRevision;
        @SuppressWarnings("unused") // Forge constructs incoming packets through reflection.
        public PlaybackSnapshot() { }

        public PlaybackSnapshot(int dimensionId, BlockPos crankPosition, String soundEventId, double bpm,
                                long playheadTicks, long serverWorldTick, float fadeGain,
                                boolean turning, long selectionRevision) {
            this.dimensionId = dimensionId;
            this.packedCrankPosition = crankPosition.toLong();
            this.soundEventId = soundEventId;
            this.bpm = bpm;
            this.playheadTicks = playheadTicks;
            this.serverWorldTick = serverWorldTick;
            this.fadeGain = fadeGain;
            this.turning = turning;
            this.selectionRevision = selectionRevision;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            dimensionId = buffer.readInt();
            packedCrankPosition = buffer.readLong();
            soundEventId = ByteBufUtils.readUTF8String(buffer);
            bpm = buffer.readDouble();
            playheadTicks = buffer.readLong();
            serverWorldTick = buffer.readLong();
            fadeGain = buffer.readFloat();
            turning = buffer.readBoolean();
            selectionRevision = buffer.readLong();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(dimensionId);
            buffer.writeLong(packedCrankPosition);
            ByteBufUtils.writeUTF8String(buffer, soundEventId);
            buffer.writeDouble(bpm);
            buffer.writeLong(playheadTicks);
            buffer.writeLong(serverWorldTick);
            buffer.writeFloat(fadeGain);
            buffer.writeBoolean(turning);
            buffer.writeLong(selectionRevision);
        }
    }
    public static final class PlaybackSnapshotHandler implements IMessageHandler<PlaybackSnapshot, IMessage> {
        @Override
        public IMessage onMessage(PlaybackSnapshot message, MessageContext context) {
            SpinMeRound.proxy.receivePlaybackUpdate(message);
            return null;
        }
    }

    /** A short notice shown above the normal action bar on the receiving client. */
    public static final class PlaybackNotification implements IMessage {
        private String text = "";

        @SuppressWarnings("unused") // Forge constructs received packets through reflection.
        public PlaybackNotification() { }

        public PlaybackNotification(String text) {
            this.text = text;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            text = ByteBufUtils.readUTF8String(buffer);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            ByteBufUtils.writeUTF8String(buffer, text);
        }
    }

    public static final class PlaybackNotificationHandler implements IMessageHandler<PlaybackNotification, IMessage> {
        @Override
        public IMessage onMessage(PlaybackNotification message, MessageContext context) {
            SpinMeRound.proxy.receiveNotification(message.text);
            return null;
        }
    }

    /** Asks for the current playback state when a crank enters a listener's range. */
    public static final class PlaybackStateRequest implements IMessage {
        private int dimensionId;
        private long packedCrankPosition;

        @SuppressWarnings("unused") // Forge constructs received packets through reflection.
        public PlaybackStateRequest() { }

        public PlaybackStateRequest(int dimensionId, BlockPos crankPosition) {
            this.dimensionId = dimensionId;
            this.packedCrankPosition = crankPosition.toLong();
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            dimensionId = buffer.readInt();
            packedCrankPosition = buffer.readLong();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(dimensionId);
            buffer.writeLong(packedCrankPosition);
        }
    }
    public static final class PlaybackStateRequestHandler implements IMessageHandler<PlaybackStateRequest, IMessage> {
        @Override
        public IMessage onMessage(PlaybackStateRequest message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                BlockPos crankPosition = BlockPos.fromLong(message.packedCrankPosition);
                if (player.dimension == message.dimensionId
                        && player.getDistanceSq(crankPosition) <= SYNC_RADIUS_BLOCKS * SYNC_RADIUS_BLOCKS
                        && player.world.isBlockLoaded(crankPosition)) {
                    ServerPlayback.INSTANCE.sendPlaybackSnapshot(player, crankPosition);
                }
            });
            return null;
        }
    }
}
