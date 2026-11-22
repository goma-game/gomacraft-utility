package gomacraft.utility.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingTest {
    private static final Setting VISIBLE = Setting.ofInt("alpha_visible", 10, 1, 50, "出す件数");

    @Test
    void readsIntsFromTheSettingsSectionWithFallback() {
        FeatureConfig config = FeatureConfig.parse(String.join("\n",
                "[features]",
                "alpha_visible = 3",
                "[settings]",
                "alpha_visible = 20  # comment",
                "beta_visible = 99",
                "gamma_visible = many"));
        assertEquals(20, VISIBLE.readInt(config), "[settings] の値を読む([features] の同じ名前は見ない)");
        assertEquals(10, Setting.ofInt("beta_visible", 10, 1, 50, "x").readInt(config), "範囲の外は既定の値");
        assertEquals(10, Setting.ofInt("gamma_visible", 10, 1, 50, "x").readInt(config), "整数でなければ既定の値");
        assertEquals(10, Setting.ofInt("delta_visible", 10, 1, 50, "x").readInt(config), "書いていなければ既定の値");
        assertEquals(10, VISIBLE.readInt(FeatureConfig.unreadable()), "読めない設定は既定の値");
        assertEquals(Optional.of(FeatureConfig.Value.INVALID), config.value("alpha_visible"),
                "[features] と [settings] は別に読む");
        assertEquals(Optional.of("20"), config.setting("alpha_visible"));
    }

    @Test
    void defaultsListTheSettings() {
        Feature f = new Feature() {
            @Override
            public String id() {
                return "alpha";
            }

            @Override
            public List<Setting> settings() {
                return List.of(VISIBLE);
            }
        };
        String text = FeatureConfig.defaults(List.of(f));
        assertTrue(text.contains("[settings]\n# 出す件数(1〜50)\nalpha_visible = 10\n"), text);
        assertEquals(10, VISIBLE.readInt(FeatureConfig.parse(text)));
    }

    @Test
    void rejectsBadDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> Setting.ofInt("Bad-Key", 1, 0, 2, "x"));
        assertThrows(IllegalArgumentException.class, () -> Setting.ofInt("ok", 5, 0, 2, "x"));
    }
}
