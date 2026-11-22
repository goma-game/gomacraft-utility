package gomacraft.utility.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gomacraft.utility.screenshot.ScreenshotRules.Target;
import gomacraft.utility.screenshot.ShareSettings.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShareSettingsTest {
    @TempDir
    Path config;

    @Test
    void writesAndReadsTheDefaults() throws Exception {
        ShareSettings first = ShareSettings.load(config);
        assertEquals(Target.CHAT, first.defaultTarget());
        assertEquals(Modifier.CTRL, first.chooserModifier());
        assertTrue(first.problems().isEmpty(), first.problems().toString());
        assertTrue(Files.exists(config.resolve(ShareSettings.FILE_NAME)));
    }

    @Test
    void readsTheChosenValues() {
        ShareSettings s = ShareSettings.parse("default_target = \"both\"\nchooser_modifier = alt\n");
        assertEquals(Target.BOTH, s.defaultTarget());
        assertEquals(Modifier.ALT, s.chooserModifier());
    }

    @Test
    void unknownValuesFallBackToTheDefaultsAndAreReported() {
        ShareSettings s = ShareSettings.parse("default_target = \"everywhere\"\nchooser_modifier = \"meta\"\nx = 1\n");
        assertEquals(Target.CHAT, s.defaultTarget());
        assertEquals(Modifier.CTRL, s.chooserModifier());
        assertEquals(3, s.problems().size());
    }
}
