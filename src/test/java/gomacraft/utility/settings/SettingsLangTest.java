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

    /** ボタンの字が使える幅(Button の幅 150 から、左右の 2 ずつ)。 */
    private static final int LABEL_WIDTH = 146;

    /**
     * ボタンの名前は短い語で、説明はツールチップに置く(長い名前はボタンからはみ出して読めない —— goma-org/gomabase#2295 の
     * 2 回目の確かめの 5)。「名前: 値」の幅を、Minecraft の既定の書体の幅で見積もる(全角は 9、英数は 6 で、狭い字は
     * 狭く数える)。⚠ 見積もりで、TTF の書体に替えた画面の幅は見ていない。
     */
    @Test
    void everyLabelHasATooltipAndFitsTheButton() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String name : List.of("en_us", "ja_jp")) {
            JsonObject json = lang(name);
            String on = name.equals("ja_jp") ? "オン" : "ON";
            String off = name.equals("ja_jp") ? "オフ" : "OFF";
            List<String> labels = new ArrayList<>();
            for (Feature f : Features.catalog(false)) {
                if (f.shownInClientSettings()) {
                    labels.add("gomacraft_utility.feature." + f.id());
                }
            }
            for (String key : json.keySet()) {
                if (key.startsWith("gomacraft_utility.settings.") && json.has(key + ".tooltip")) {
                    labels.add(key);
                }
            }
            for (String key : labels) {
                if (!json.has(key + ".tooltip")) {
                    problems.add(name + ": ツールチップが無い: " + key);
                }
                if (!json.has(key)) {
                    continue;
                }
                // 値: 選ぶ語(<鍵>.<語>)があればその一番長いもの、無ければ入り切りか数(1000 まで)
                int value = Math.max(width(on), Math.max(width(off), width("1000")));
                for (String other : json.keySet()) {
                    if (other.startsWith(key + ".") && !other.equals(key + ".tooltip")) {
                        value = Math.max(value, width(json.get(other).getAsString()));
                    }
                }
                int total = width(json.get(key).getAsString()) + width(": ") + value;
                if (total > LABEL_WIDTH) {
                    problems.add(name + ": " + key + " の名前が長い(見積もり " + total + " > " + LABEL_WIDTH + ")");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Minecraft の既定の書体での幅の見積もり。 */
    static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (c > 0x7F) {
                w += 9;
            } else if ("il!|.,:;'`".indexOf(c) >= 0) {
                w += 2;
            } else if (c == ' ' || c == 'I' || c == 't' || c == '[' || c == ']' || c == '(' || c == ')') {
                w += 4;
            } else if (c == 'f' || c == 'k' || c == '<' || c == '>' || c == '"') {
                w += 5;
            } else if (c == '@' || c == '~') {
                w += 7;
            } else {
                w += 6;
            }
        }
        return w;
    }
}
