package dev.omatheusmesmo.qlawkus.console.voice;

import java.io.ByteArrayOutputStream;
import java.time.Duration;

/**
 * Bounded accumulation of one utterance's PCM. The byte limit is the smaller of the configured byte
 * budget and the duration budget at the session's sample rate, and a chunk that would cross it is
 * refused whole, so the buffer never holds part of a frame.
 */
final class UtteranceBuffer {

    private static final int SAMPLE_BYTES = 2;

    private final int sampleRate;
    private final int limitBytes;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();

    UtteranceBuffer(int sampleRate, long maxBytes, Duration maxDuration) {
        this.sampleRate = sampleRate;
        long byDuration = maxDuration.toMillis() * sampleRate * SAMPLE_BYTES / 1000L;
        this.limitBytes = (int) Math.min(maxBytes, byDuration);
    }

    int limitBytes() {
        return limitBytes;
    }

    int size() {
        return pcm.size();
    }

    boolean append(byte[] chunk) {
        if (chunk.length % SAMPLE_BYTES != 0 || pcm.size() + chunk.length > limitBytes) {
            return false;
        }
        pcm.write(chunk, 0, chunk.length);
        return true;
    }

    byte[] take() {
        byte[] samples = pcm.toByteArray();
        pcm.reset();
        return WavEncoder.pcm16Mono(samples, sampleRate);
    }
}
