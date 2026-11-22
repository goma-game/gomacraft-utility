package gomacraft.utility.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gomacraft.utility.screenshot.ScreenshotRules.Target;
import gomacraft.utility.settings.ClientSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShareSettingsMigrationTest {
    private static ClientSettings settings() {
        ClientSettings s = new ClientSettings();
        s.register(ShareSettings.DEFAULT_TARGET);
        s.register(ShareSettings.CHOOSER_MODIFIER);
        return s;
    }

    @Test
    void movesTheLegacyFileOnceAndKeepsIt(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(ShareSettings.FILE_NAME), "default_target = \"both\"\nchooser_modifier = \"alt\"\n");
        ClientSettings s = settings();
        s.parse("");
        assertTrue(ShareSettings.migrateLegacy(s, dir, w -> { }));
        ShareSettings read = ShareSettings.from(s);
        assertEquals(Target.BOTH, read.defaultTarget());
        assertEquals(ShareSettings.Modifier.ALT, read.chooserModifier());
        assertTrue(Files.exists(dir.resolve(ShareSettings.FILE_NAME)), "前の置き場を消した");
        // 二度目は移さない(手元の設定に書かれた値が正)
        assertFalse(ShareSettings.migrateLegacy(s, dir, w -> { }));
    }

    @Test
    void doesNotOverwriteValuesAlreadyInTheClientSettings(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(ShareSettings.FILE_NAME), "default_target = \"both\"\n");
        ClientSettings s = settings();
        s.parse("[screenshot_share]\ndefault_target = \"screenshot\"\n");
        assertFalse(ShareSettings.migrateLegacy(s, dir, w -> { }));
        assertEquals(Target.SCREENSHOT, ShareSettings.from(s).defaultTarget());
    }

    @Test
    void defaultsWithoutAnyFile(@TempDir Path dir) throws Exception {
        ClientSettings s = settings();
        s.parse("");
        assertFalse(ShareSettings.migrateLegacy(s, dir, w -> { }));
        assertEquals(Target.CHAT, ShareSettings.from(s).defaultTarget());
        assertEquals(ShareSettings.Modifier.CTRL, ShareSettings.from(s).chooserModifier());
    }
}
