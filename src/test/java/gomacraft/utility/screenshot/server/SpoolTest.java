package gomacraft.utility.screenshot.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gomacraft.utility.screenshot.ScreenshotRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpoolTest {
    @TempDir
    Path game;

    @Test
    void writesTheImageAndThenTheMeta() throws IOException {
        Path dir = Spool.dir(game);
        byte[] image = InboxStateTest.png(100);
        UUID id = UUID.fromString("0123abcd-0000-0000-0000-000000000000");
        String stem = Spool.write(dir, image, ScreenshotRules.Format.PNG, "player_one", id,
                Instant.parse("2026-11-22T01:02:03Z"), 1);
        assertEquals("20261122T010203Z-0123abcd-00000001", stem);
        assertArrayEquals(image, Files.readAllBytes(dir.resolve(stem + ".png")));
        JsonObject meta = JsonParser.parseString(
                Files.readString(dir.resolve(stem + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("player_one", meta.get("player").getAsString());
        assertEquals(id.toString(), meta.get("uuid").getAsString());
        assertEquals("png", meta.get("format").getAsString());
        assertEquals("2026-11-22T01:02:03Z", meta.get("taken_at").getAsString());
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(List.of(stem + ".json", stem + ".png"), names);
        }
    }

    @Test
    void isFullAtTheFileLimit() throws IOException {
        Path dir = Spool.dir(game);
        assertFalse(Spool.full(dir));
        for (int i = 0; i < ScreenshotRules.SPOOL_MAX_FILES; i++) {
            assertFalse(Spool.full(dir));
            Spool.write(dir, InboxStateTest.png(10), ScreenshotRules.Format.PNG, "p", UUID.randomUUID(),
                    Instant.now(), i);
        }
        assertTrue(Spool.full(dir));
    }
}
