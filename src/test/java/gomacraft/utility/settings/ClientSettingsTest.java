package gomacraft.utility.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    void linesAfterAnUnreadableSectionAreNotReadAsThePreviousSection() {
        ClientSettings s = settings();
        s.parse("""
                [emi_ae_quick_craft]
                skip_confirm = false
                [Other.Mod]
                skip_confirm = true
                """);
        assertEquals(false, s.get(SKIP));
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
    void savingChangesOnlyTheValueLinesAndKeepsHandWrittenLines(@TempDir Path dir) throws Exception {
        String before = """
                # 自分のメモ
                [emi_ae_quick_craft]
                skip_confirm = false  # 量はその場で決めたい
                これは読めない行

                [search_history]
                size = 20
                """;
        Files.writeString(dir.resolve(ClientSettings.FILE_NAME), before);
        ClientSettings s = settings();
        s.load(dir, w -> { });
        s.set(SKIP, true);
        s.save();
        String after = Files.readString(dir.resolve(ClientSettings.FILE_NAME));
        assertTrue(after.startsWith("""
                # 自分のメモ
                [emi_ae_quick_craft]
                skip_confirm = true  # 量はその場で決めたい
                これは読めない行

                [search_history]
                size = 20
                """), after);
        // 書かれていなかった項目は、無い節ごと足す
        assertTrue(after.contains("[screenshot_share]\ndefault_target = \"chat\""), after);
    }

    @Test
    void anUnreadableFileIsNotOverwritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ClientSettings.FILE_NAME);
        byte[] notUtf8 = {(byte) 0xff, (byte) 0xfe, 'x', '\n'};
        Files.write(file, notUtf8);
        ClientSettings s = settings();
        List<String> warnings = new ArrayList<>();
        s.load(dir, warnings::add);
        assertEquals(false, s.get(SKIP), "読めなければ既定の値で動く");
        s.set(SKIP, true);
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, s::save);
        org.junit.jupiter.api.Assertions.assertArrayEquals(notUtf8, Files.readAllBytes(file), "読めないファイルを書き換えた");
        assertTrue(!warnings.isEmpty());
    }

    @Test
    void createsTheFileWithDefaults(@TempDir Path dir) throws Exception {
        ClientSettings s = settings();
        s.load(dir, w -> { });
        String text = Files.readString(dir.resolve(ClientSettings.FILE_NAME));
        assertTrue(text.contains("[emi_ae_quick_craft]\nskip_confirm = false"), text);
        assertTrue(text.contains("default_target = \"chat\""), text);
    }

    @Test
    void updateAndSaveCommitsAllChanges(@TempDir Path dir) throws Exception {
        ClientSettings s = settings();
        s.load(dir, w -> { });
        AtomicInteger fired = new AtomicInteger();
        s.onChange(fired::incrementAndGet);

        s.updateAndSave(Map.of(SKIP, true, TARGET, "both"));
        assertEquals(true, s.get(SKIP));
        assertEquals("both", s.get(TARGET));
        assertEquals(1, fired.get());

        ClientSettings reloaded = settings();
        reloaded.load(dir, w -> { });
        assertEquals(true, reloaded.get(SKIP));
        assertEquals("both", reloaded.get(TARGET));
    }

    @Test
    void updateAndSaveRestoresMemoryOnFailure(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ClientSettings.FILE_NAME);
        byte[] notUtf8 = {(byte) 0xff, (byte) 0xfe, 'x', '\n'};
        Files.write(file, notUtf8);
        ClientSettings s = settings();
        s.load(dir, w -> { });
        AtomicInteger fired = new AtomicInteger();
        s.onChange(fired::incrementAndGet);

        assertThrows(IOException.class, () -> s.updateAndSave(Map.of(SKIP, true, TARGET, "both")));
        assertFalse(s.get(SKIP), "保存失敗時はメモリ上の変更も元に戻す");
        assertEquals("chat", s.get(TARGET));
        assertEquals(0, fired.get(), "保存失敗時は変更通知を出さない");
    }
}
