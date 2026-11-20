package dev.omatheusmesmo.qlawkus.console.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UtteranceBufferTest {

    @Test
    void theByteLimitIsTheSmallerOfTheByteBudgetAndTheDurationBudget() {
        UtteranceBuffer buffer = new UtteranceBuffer(16_000, 1_000_000, Duration.ofSeconds(1));

        assertEquals(32_000, buffer.limitBytes(), "one second of 16 kHz mono PCM16 is 32000 bytes");
    }

    @Test
    void refusesAChunkThatWouldExceedTheLimitAndKeepsWhatWasAlreadyReceived() {
        UtteranceBuffer buffer = new UtteranceBuffer(8_000, 6, Duration.ofMinutes(1));

        assertTrue(buffer.append(new byte[4]));
        assertFalse(buffer.append(new byte[4]), "4 + 4 bytes is over the 6 byte budget");
        assertEquals(4, buffer.size());
    }

    @Test
    void refusesAnOddLengthChunkBecauseSamplesAreTwoBytes() {
        UtteranceBuffer buffer = new UtteranceBuffer(8_000, 100, Duration.ofMinutes(1));

        assertFalse(buffer.append(new byte[3]));
    }

    @Test
    void takingTheUtteranceWrapsItAsAWavFileAndEmptiesTheBuffer() {
        UtteranceBuffer buffer = new UtteranceBuffer(16_000, 1_000, Duration.ofMinutes(1));
        byte[] pcm = {1, 0, 2, 0};
        buffer.append(pcm);

        byte[] wav = buffer.take();

        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(wav, 8, 4, StandardCharsets.US_ASCII));
        assertEquals(1, header.getShort(22), "mono");
        assertEquals(16_000, header.getInt(24), "sample rate");
        assertEquals(16, header.getShort(34), "16 bits per sample");
        assertEquals(4, header.getInt(40), "data size");
        assertArrayEquals(pcm, Arrays.copyOfRange(wav, 44, 48));
        assertEquals(0, buffer.size());
    }
}
