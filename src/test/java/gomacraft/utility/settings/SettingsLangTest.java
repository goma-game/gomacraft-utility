package gomacraft.utility.settings;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gomacraft.utility.Features;
import gomacraft.utility.feature.Feature;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
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
            for (ClientSettings.Setting<?> setting : declaredSettings()) {
                labels.add(setting.translationKey());
            }
            for (String key : labels) {
                if (!json.has(key)) {
                    problems.add(name + ": 名前の訳が無い: " + key);
                    continue;
                }
                if (!json.has(key + ".tooltip")) {
                    problems.add(name + ": ツールチップが無い: " + key);
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

    /**
     * 宣言した設定の項目(Mod の class のうち、画面の側({@code .client.})と mixin を除いたものの、static の
     * {@link ClientSettings.Setting} の field)。項目は Minecraft に依らない class に置く決まり(例
     * {@code SearchHistorySettings})。⚠ 読めない class は飛ばさずに落とす(飛ばした分は見られない)。
     */
    static List<ClientSettings.Setting<?>> declaredSettings() throws Exception {
        URL base = Features.class.getProtectionDomain().getCodeSource().getLocation();
        Path root = Path.of(base.toURI());
        List<ClientSettings.Setting<?>> out = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root.resolve("gomacraft/utility"))) {
            files = walk.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        }
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/').replace('/', '.');
            name = name.substring(0, name.length() - ".class".length());
            if (name.contains(".client.") || name.contains(".mixin.") || name.contains("$")) {
                continue;
            }
            Class<?> type = Class.forName(name, false, SettingsLangTest.class.getClassLoader());
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        && ClientSettings.Setting.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    out.add((ClientSettings.Setting<?>) field.get(null));
                }
            }
        }
        return out;
    }

    @Test
    void declaredSettingsAreFound() throws Exception {
        // 見つけ方が黙って空にならない(alt_click・keep・投稿先のどれも見える)
        List<String> keys = declaredSettings().stream().map(ClientSettings.Setting::translationKey).toList();
        for (String key : List.of("gomacraft_utility.settings.emi_ae_quick_craft.alt_click",
                "gomacraft_utility.settings.ae2_search_history.keep",
                "gomacraft_utility.settings.screenshot_share.default_target")) {
            assertTrue(keys.contains(key), "設定の項目が見つからない: " + key + " / " + keys);
        }
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
