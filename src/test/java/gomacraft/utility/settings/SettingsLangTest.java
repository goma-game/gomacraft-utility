package gomacraft.utility.settings;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gomacraft.utility.Features;
import gomacraft.utility.feature.Feature;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 設定の画面に出す機能の名前の訳が、en_us と ja_jp の両方に在ること(無いと画面に訳の鍵がそのまま出る —— 機能を
 * 足した担当が訳を忘れた前例がある。#17 のレビュー)。
 */
class SettingsLangTest {
    private static JsonObject lang(String name) throws Exception {
        try (InputStream in = SettingsLangTest.class.getClassLoader()
                .getResourceAsStream("assets/gomacraft_utility/lang/" + name + ".json")) {
            assertNotNull(in, name + " が無い");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyFeatureShownOnTheScreenHasANameInBothLanguages() throws Exception {
        List<String> missing = new ArrayList<>();
        for (String name : List.of("en_us", "ja_jp")) {
            JsonObject json = lang(name);
            for (Feature f : Features.catalog(false)) {
                String key = "gomacraft_utility.feature." + f.id();
                if (f.shownInClientSettings() && !json.has(key)) {
                    missing.add(name + ": " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "設定の画面に出す機能の名前の訳が無い: " + missing);
    }
}
