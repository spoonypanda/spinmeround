package com.spoonypanda.spinmeround.network;

import com.spoonypanda.spinmeround.SpinMeRound;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PlaybackNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(SpinMeRound.MOD_ID, "playback"), () -> "1", "1"::equals, "1"::equals);
    // Installed by the client event subscriber; common code never references client classes.
    public static Consumer<Snapshot> receiver = snapshot -> {};

    public static void init() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, (snapshot, supplier) -> {
            var context = supplier.get();
            context.enqueueWork(() -> receiver.accept(snapshot));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public record Snapshot(ResourceLocation dimension, BlockPos pos, String sound, double bpm,
                           long ticks, long worldTick, float gain, long revision, boolean loop, int volume) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeBlockPos(pos);
            buffer.writeUtf(sound, 256);
            buffer.writeDouble(bpm);
            buffer.writeVarLong(ticks);
            buffer.writeLong(worldTick);
            buffer.writeFloat(gain);
            buffer.writeLong(revision);
            buffer.writeBoolean(loop);
            buffer.writeVarInt(volume);
        }

        public static Snapshot decode(FriendlyByteBuf buffer) {
            return new Snapshot(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readUtf(256),
                    buffer.readDouble(), buffer.readVarLong(), buffer.readLong(), buffer.readFloat(),
                    buffer.readLong(), buffer.readBoolean(), buffer.readVarInt());
        }
    }

    private PlaybackNetwork() {}
}
