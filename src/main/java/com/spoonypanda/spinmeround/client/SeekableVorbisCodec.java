package com.spoonypanda.spinmeround.client;

import paulscode.sound.SoundBuffer;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.ICodec;
import paulscode.sound.codecs.CodecJOrbis;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;

/** Decodes to a shared playhead before handing PCM to Paulscode. */
public final class SeekableVorbisCodec implements ICodec {
    public abstract static class SeekableAudioConnection extends URLConnection {
        protected SeekableAudioConnection(URL url) {
            super(url);
        }

        public abstract long requestedOffsetMillis();

        public abstract boolean shouldLoop();

        public abstract void commitInitialSeek(long requestedOffsetMillis);
    }
    private SoundBuffer pendingPcm;
    private SeekableAudioConnection seekConnection;
    private long requestedSeekMillis;
    private URL sourceUrl;
    private boolean hasReadAudio;
    private CodecJOrbis decoder;

    @Override
    public boolean initialize(URL url) {
        if (pendingPcm != null) {
            pendingPcm.cleanup();
        }
        pendingPcm = null;
        sourceUrl = url;
        hasReadAudio = false;
        try {
            URLConnection connection = url.openConnection();
            seekConnection = connection instanceof SeekableAudioConnection ? (SeekableAudioConnection) connection : null;
            requestedSeekMillis = seekConnection == null ? 0 : Math.max(0, seekConnection.requestedOffsetMillis());
            if (!resetDecoder(url)) {
                return false;
            }
            AudioFormat audioFormat = getAudioFormat();
            if (audioFormat == null) {
                throw new IOException("Crank audio decoder did not provide an audio format");
            }
            int frameSizeBytes = audioFormat.getFrameSize();
            // Decode/discard complete PCM frames until the requested playhead is reached.
            long bytesToSkip = (long) (requestedSeekMillis * (audioFormat.getFrameRate() / 1000.0)) * frameSizeBytes;
            long decodedBytesSinceReset = 0;
            while (bytesToSkip > 0) {
                SoundBuffer buffer = decoder.read();
                if (buffer == null || buffer.audioData == null || buffer.audioData.length == 0) {
                    if (seekConnection == null || !seekConnection.shouldLoop() || decodedBytesSinceReset == 0) {
                        return true;
                    }
                    // A seek beyond the song's end wraps by its decoded length.
                    bytesToSkip %= decodedBytesSinceReset;
                    if (!resetDecoder(url)) {
                        return false;
                    }
                    decodedBytesSinceReset = 0;
                    continue;
                }
                int bufferBytes = buffer.audioData.length;
                decodedBytesSinceReset += bufferBytes;
                if (bytesToSkip < bufferBytes) {
                    pendingPcm = new SoundBuffer(Arrays.copyOfRange(buffer.audioData, (int) bytesToSkip, bufferBytes), buffer.audioFormat);
                    bytesToSkip = 0;
                } else {
                    bytesToSkip -= bufferBytes;
                }
                buffer.cleanup();
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            SoundSystemConfig.getLogger().errorMessage("SeekableVorbisCodec",
                    "Unable to seek crank audio: " + exception.getMessage(), 0);
            cleanup();
            return false;
        }
    }

    @Override
    public SoundBuffer read() {
        SoundBuffer buffer = pendingPcm;
        pendingPcm = null;
        if (buffer == null) {
            buffer = decoder == null ? null : decoder.read();
        }
        if (buffer == null && !hasReadAudio && requestedSeekMillis > 0 && seekConnection != null && seekConnection.shouldLoop()) {
            if (resetDecoder(sourceUrl)) {
                buffer = decoder.read();
            }
        }
        // Paulscode may initialize for format discovery; commit only when audio is read.
        if (seekConnection != null) {
            seekConnection.commitInitialSeek(requestedSeekMillis);
        }
        hasReadAudio = true;
        return buffer;
    }

    private boolean resetDecoder(URL url) {
        if (decoder != null) {
            decoder.cleanup();
        }
        // A cleaned-up CodecJOrbis instance cannot safely be reinitialized.
        decoder = new CodecJOrbis();
        return decoder.initialize(url);
    }

    @Override
    public void reverseByteOrder(boolean reverse) { }

    @Override
    public boolean initialized() {
        return decoder != null && decoder.initialized();
    }

    @Override
    public boolean endOfStream() {
        return pendingPcm == null && (decoder == null || decoder.endOfStream());
    }

    @Override
    public AudioFormat getAudioFormat() {
        return decoder == null ? null : decoder.getAudioFormat();
    }

    @Override
    public void cleanup() {
        if (pendingPcm != null) {
            pendingPcm.cleanup();
        }
        pendingPcm = null;
        if (decoder != null) {
            decoder.cleanup();
            decoder = null;
        }
    }

    @Override
    public SoundBuffer readAll() {
        ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();
        SoundBuffer buffer;
        while ((buffer = read()) != null && buffer.audioData != null && buffer.audioData.length > 0) {
            pcmOutput.write(buffer.audioData, 0, buffer.audioData.length);
            buffer.cleanup();
        }
        return new SoundBuffer(pcmOutput.toByteArray(), getAudioFormat());
    }
}
