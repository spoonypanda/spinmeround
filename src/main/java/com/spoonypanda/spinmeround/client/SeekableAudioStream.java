package com.spoonypanda.spinmeround.client;

import com.mojang.blaze3d.audio.OggAudioStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

/** Decode/discard to the shared playhead on the audio worker, then stream normally. */
final class SeekableAudioStream implements AudioStream {
    private final ResourceProvider resources;
    private final ResourceLocation path;
    private final boolean loop;
    private OggAudioStream decoder;
    private ByteBuffer pending;

    SeekableAudioStream(ResourceProvider resources, ResourceLocation path, long ticks, boolean loop) throws IOException {
        this.resources = resources;
        this.path = path;
        this.loop = loop;
        try {
            reopen();
            var format = decoder.getFormat();
            long frames = (long) (Math.max(0, ticks) * (format.getFrameRate() / 20.0));
            long remaining = frames > Long.MAX_VALUE / format.getFrameSize() ? Long.MAX_VALUE : frames * format.getFrameSize();
            long decoded = 0;
            while (remaining > 0) {
                ByteBuffer buffer = decoder.read(65536);
                int size = buffer.remaining();
                if (size == 0) {
                    if (!loop || decoded == 0) break;
                    // Measure at most one full song before wrapping even very old playheads.
                    remaining %= decoded;
                    reopen();
                    decoded = 0;
                } else {
                    decoded += size;
                    int skip = (int) Math.min(remaining, size);
                    buffer.position(buffer.position() + skip);
                    remaining -= skip;
                    if (buffer.hasRemaining()) pending = buffer;
                }
            }
        } catch (IOException | RuntimeException exception) {
            try { close(); } catch (IOException closeFailure) { exception.addSuppressed(closeFailure); }
            throw exception;
        }
    }

    private void reopen() throws IOException {
        if (decoder != null) decoder.close();
        var input = resources.open(path);
        try {
            decoder = new OggAudioStream(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    @Override
    public AudioFormat getFormat() { return decoder.getFormat(); }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (pending != null) {
            ByteBuffer result = pending;
            pending = null;
            return result;
        }
        ByteBuffer buffer = decoder.read(size);
        if (!buffer.hasRemaining() && loop) {
            reopen();
            buffer = decoder.read(size);
        }
        return buffer;
    }

    @Override
    public void close() throws IOException {
        pending = null;
        if (decoder != null) decoder.close();
    }
}
