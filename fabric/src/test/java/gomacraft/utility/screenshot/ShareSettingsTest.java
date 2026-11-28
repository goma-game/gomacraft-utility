package gomacraft.utility.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gomacraft.utility.screenshot.ScreenshotRules.Target;
import gomacraft.utility.screenshot.ShareSettings.Modifier;
import gomacraft.utility.settings.ClientSettings;
import org.junit.jupiter.api.Test;

/** fabric 版: forge 版の同じ名前の試験から写し、前の置き場のファイルの試験を、手元の設定から作る試験に替えた。 */
class ShareSettingsTest {
    @Test
    void defaultsComeFromTheClientSettings() {
        ClientSettings client = new ClientSettings();
        client.register(ShareSettings.DEFAULT_TARGET);
        client.register(ShareSettings.CHOOSER_MODIFIER);
        ShareSettings first = ShareSettings.from(client);
        assertEquals(Target.CHAT, first.defaultTarget());
        assertEquals(Modifier.CTRL, first.chooserModifier());
        assertTrue(first.problems().isEmpty(), first.problems().toString());

        client.set(ShareSettings.DEFAULT_TARGET, "both");
        client.set(ShareSettings.CHOOSER_MODIFIER, "alt");
        ShareSettings changed = ShareSettings.from(client);
        assertEquals(Target.BOTH, changed.defaultTarget());
        assertEquals(Modifier.ALT, changed.chooserModifier());
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
