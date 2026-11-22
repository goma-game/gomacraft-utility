package gomacraft.utility.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientSettingsTest {
    private static final ClientSettings.Bool SKIP = new ClientSettings.Bool("emi_ae_quick_craft", "skip_confirm", false);
    private static final ClientSettings.Int SIZE = new ClientSettings.Int("search_history", "size", 10, 1, 100);
    private static final ClientSettings.Choice TARGET =
            new ClientSettings.Choice("screenshot_share", "default_target", "chat", List.of("chat", "screenshot", "both"));

    private static ClientSettings settings() {
        ClientSettings s = new ClientSettings();
        s.register(SKIP);
        s.register(SIZE);
        s.register(TARGET);
        return s;
    }

    @Test
    void defaultsWhenNotWrittenAndWhenUnreadable() {
        ClientSettings s = settings();
        s.parse("""
                [emi_ae_quick_craft]
                skip_confirm = yes
                [search_history]
                size = 1000
                """);
        // 読めない値は既定に倒し、黙らずに出す
        assertEquals(false, s.get(SKIP));
        assertEquals(10, s.get(SIZE));
        assertEquals("chat", s.get(TARGET));
        assertEquals(2, s.problems().size(), s.problems().toString());
    }

    @Test
    void readsWrittenValues() {
        ClientSettings s = settings();
        s.parse("""
                [emi_ae_quick_craft]
                skip_confirm = true  # 一気に作る
                [screenshot_share]
                default_target = "Both"
                """);
        assertEquals(true, s.get(SKIP));
        assertEquals("both", s.get(TARGET));
        assertTrue(s.problems().isEmpty(), s.problems().toString());
    }

    @Test
    void saveKeepsUnknownEntriesAndRoundTrips(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(ClientSettings.FILE_NAME), """
                [newer_feature]
                something = 3
                [search_history]
                size = 20
                """);
        ClientSettings s = settings();
        List<String> warnings = new ArrayList<>();
        s.load(dir, warnings::add);
        assertEquals(20, s.get(SIZE));
        s.set(SKIP, true);
        s.save();

        ClientSettings again = settings();
        again.load(dir, warnings::add);
        assertEquals(true, again.get(SKIP));
        assertEquals(20, again.get(SIZE));
        String text = Files.readString(dir.resolve(ClientSettings.FILE_NAME));
        assertTrue(text.contains("[newer_feature]\nsomething = 3"), "知らない項目を消した: " + text);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void createsTheFileWithDefaults(@TempDir Path dir) throws Exception {
        ClientSettings s = settings();
        s.load(dir, w -> { });
        String text = Files.readString(dir.resolve(ClientSettings.FILE_NAME));
        assertTrue(text.contains("[emi_ae_quick_craft]\nskip_confirm = false"), text);
        assertTrue(text.contains("default_target = \"chat\""), text);
    }
}
