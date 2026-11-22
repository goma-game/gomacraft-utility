package gomacraft.utility.screenshot.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import gomacraft.utility.screenshot.ScreenshotRules;
import gomacraft.utility.screenshot.ScreenshotRules.Result;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboxStateTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    /** PNG の印で始まる、{@code size} バイトの中身。 */
    static byte[] png(int size) {
        byte[] data = new byte[size];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(sig, 0, data, 0, sig.length);
        for (int i = sig.length; i < size; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    static byte[] piece(byte[] data, int index) {
        int from = index * ScreenshotRules.CHUNK_BYTES;
        return Arrays.copyOfRange(data, from, from + ScreenshotRules.chunkLength(data.length, index));
    }

    @Test
    void assemblesChunksInOrder() {
        InboxState state = new InboxState();
        byte[] data = png(ScreenshotRules.CHUNK_BYTES * 2 + 10);
        assertNull(state.accept(A, 7, data.length, 0, piece(data, 0), 0, false).result());
        assertNull(state.accept(A, 7, data.length, 1, piece(data, 1), 0, false).done());
        InboxState.Step last = state.accept(A, 7, data.length, 2, piece(data, 2), 0, false);
        assertNull(last.result());
        assertArrayEquals(data, last.done());
    }

    @Test
    void dropsAnUploadWhoseChunksAreOutOfOrderOrMisSized() {
        InboxState state = new InboxState();
        byte[] data = png(ScreenshotRules.CHUNK_BYTES * 3);
        state.accept(A, 1, data.length, 0, piece(data, 0), 0, false);
        assertEquals(Result.BROKEN, state.accept(A, 1, data.length, 2, piece(data, 2), 0, false).result());
        // 捨てた送りの残りは黙って捨てる
        InboxState.Step quiet = state.accept(A, 1, data.length, 1, piece(data, 1), 0, false);
        assertNull(quiet.result());
        assertNull(quiet.done());

        InboxState other = new InboxState();
        other.accept(B, 1, data.length, 0, piece(data, 0), 0, false);
        assertEquals(Result.BROKEN, other.accept(B, 1, data.length + 1, 1, piece(data, 1), 0, false).result());
        other.accept(C, 2, data.length, 0, piece(data, 0), 0, false);
        assertEquals(Result.BROKEN, other.accept(C, 2, data.length, 1, new byte[5], 0, false).result());
    }

    @Test
    void refusesWhatTheFirstChunkDeclaresBadly() {
        InboxState state = new InboxState();
        byte[] head = piece(png(ScreenshotRules.CHUNK_BYTES), 0);
        assertEquals(Result.TOO_LARGE,
                state.accept(A, 1, ScreenshotRules.MAX_BYTES + 1, 0, head, 0, false).result());
        assertEquals(Result.TOO_LARGE, state.accept(A, 1, 0, 0, head, 0, false).result());
        assertEquals(Result.BAD_IMAGE,
                state.accept(A, 1, head.length, 0, new byte[head.length], 0, false).result());
        assertEquals(Result.SPOOL_FULL, state.accept(A, 1, head.length, 0, head, 0, true).result());
    }

    @Test
    void onePerPlayerACooldownAndAServerWideLimit() {
        InboxState state = new InboxState();
        byte[] data = png(ScreenshotRules.CHUNK_BYTES * 2);
        state.accept(A, 1, data.length, 0, piece(data, 0), 0, false);
        assertEquals(Result.BUSY, state.accept(A, 2, data.length, 0, piece(data, 0), 0, false).result());
        state.accept(B, 1, data.length, 0, piece(data, 0), 0, false);
        // 全体で 2 本まで
        assertEquals(Result.BUSY, state.accept(C, 1, data.length, 0, piece(data, 0), 0, false).result());
        assertNotNull(state.accept(A, 1, data.length, 1, piece(data, 1), 10, false).done());
        // 書いている間も数える
        assertEquals(Result.BUSY, state.accept(C, 1, data.length, 0, piece(data, 0), 10, false).result());
        state.written();
        // A は始めてから 30 秒は次を始められない
        assertEquals(Result.COOLDOWN, state.accept(A, 3, data.length, 0, piece(data, 0), 20, false).result());
        assertNull(state.accept(A, 3, data.length, 0, piece(data, 0),
                ScreenshotRules.COOLDOWN_MILLIS + 1, false).result());
    }

    @Test
    void abandonedUploadsAreDroppedAfterTheTimeout() {
        InboxState state = new InboxState();
        byte[] data = png(ScreenshotRules.CHUNK_BYTES * 2);
        state.accept(A, 1, data.length, 0, piece(data, 0), 0, false);
        assertEquals(0, state.prune(ScreenshotRules.TIMEOUT_MILLIS));
        assertEquals(1, state.prune(ScreenshotRules.TIMEOUT_MILLIS + 1));
        assertEquals(0, state.inFlight());
        state.accept(B, 1, data.length, 0, piece(data, 0), 0, false);
        state.forget(B);
        assertEquals(0, state.inFlight());
    }
}
