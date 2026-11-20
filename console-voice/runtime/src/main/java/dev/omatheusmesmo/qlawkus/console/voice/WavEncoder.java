package dev.omatheusmesmo.qlawkus.console.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Wraps raw 16-bit little-endian mono PCM in a canonical 44-byte WAV header, so the browser's
 * captured samples can go to the speech-to-text endpoint as a self-describing file.
 */
final class WavEncoder {

    private static final int HEADER_BYTES = 44;
    private static final short PCM_FORMAT = 1;
    private static final short CHANNELS = 1;
    private static final short BITS_PER_SAMPLE = 16;

    private WavEncoder() {
    }

    static byte[] pcm16Mono(byte[] pcm, int sampleRate) {
        ByteBuffer out = ByteBuffer.allocate(HEADER_BYTES + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        int byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8;
        short blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;
        out.put(ascii("RIFF")).putInt(36 + pcm.length).put(ascii("WAVE"));
        out.put(ascii("fmt ")).putInt(16).putShort(PCM_FORMAT).putShort(CHANNELS).putInt(sampleRate);
        out.putInt(byteRate).putShort(blockAlign).putShort(BITS_PER_SAMPLE);
        out.put(ascii("data")).putInt(pcm.length).put(pcm);
        return out.array();
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
