package gomacraft.utility.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Test
    void enUsAndJaJpHaveMatchingKeysAndPlaceholders() throws Exception {
        JsonObject en = lang("en_us");
        JsonObject ja = lang("ja_jp");
        Set<String> enKeys = new TreeSet<>(en.keySet());
        Set<String> jaKeys = new TreeSet<>(ja.keySet());
        assertEquals(enKeys, jaKeys, "en_us と ja_jp の翻訳キーが一致しない");

        Pattern placeholderPattern = Pattern.compile("(?<!%)%(?:\\d+\\$)?[sd](?![a-zA-Z])");
        List<String> mismatch = new ArrayList<>();
        for (String key : enKeys) {
            List<String> enPh = placeholders(placeholderPattern, en.get(key).getAsString());
            List<String> jaPh = placeholders(placeholderPattern, ja.get(key).getAsString());
            if (!enPh.equals(jaPh)) {
                mismatch.add(key + ": en=" + enPh + " ja=" + jaPh);
            }
        }
        assertTrue(mismatch.isEmpty(), "en_us と ja_jp のプレースホルダが一致しない: " + mismatch);
    }

    private static List<String> placeholders(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        Collections.sort(found);
        return found;
    }

    /**
     * 設定の画面の並び({@link SettingsLayout})の名前が短い語で、行に収まること(長い名前は右の操作に重なって読めない ——
     * goma-org/gomabase#2295 の 2 回目の確かめの 5)。名前の幅は行の幅から、字下げ(木の深さ)・右の操作・余白を引いた残り。
     * 選ぶ語(<鍵>.<語>)は右の操作に収まること。機能と項目にはツールチップが要る(説明はそこに置く)。幅は Minecraft の
     * 既定の書体の幅で見積もる(全角は 9、英数は 6 で、狭い字は狭く数える)。⚠ 見積もりで、TTF の書体に替えた画面の幅は
     * 見ていない。
     */
    @Test
    void everyLabelHasATooltipAndFitsTheRow() throws Exception {
        List<String> shown = new ArrayList<>();
        for (Feature f : Features.catalog(false)) {
            if (f.shownInClientSettings()) {
                shown.add(f.id());
            }
        }
        List<SettingsLayout.Tab> tabs = SettingsLayout.build(shown, declaredSettings());
        List<String> problems = new ArrayList<>();
        for (String name : List.of("en_us", "ja_jp")) {
            JsonObject json = lang(name);
            for (SettingsLayout.Tab tab : tabs) {
                if (!json.has(tab.labelKey())) {
                    problems.add(name + ": タブの名前の訳が無い: " + tab.labelKey());
                }
                SettingsLayout.walk(tab.nodes(), 0, (node, depth) -> {
                    String key = node.labelKey();
                    if (!json.has(key)) {
                        problems.add(name + ": 名前の訳が無い: " + key);
                        return;
                    }
                    if (node.tooltipKey() != null && !json.has(node.tooltipKey())) {
                        problems.add(name + ": ツールチップが無い: " + key);
                    }
                    int room = SettingsLayout.ROW_WIDTH - SettingsLayout.GAP * 3 - depth * SettingsLayout.INDENT
                            - SettingsLayout.CONTROL_WIDTH;
                    int label = width(json.get(key).getAsString());
                    if (label > room) {
                        problems.add(name + ": " + key + " の名前が長い(見積もり " + label + " > " + room + ")");
                    }
                    for (String other : json.keySet()) {
                        if (other.startsWith(key + ".") && !other.equals(key + ".tooltip")
                                && width(json.get(other).getAsString()) > SettingsLayout.CONTROL_WIDTH - 8) {
                            problems.add(name + ": " + other + " の語が操作に収まらない");
                        }
                    }
                });
            }
            for (String key : List.of("gomacraft_utility.settings.group.status", "gomacraft_utility.settings.restart",
                    "gomacraft_utility.settings.undo", "gomacraft_utility.settings.reset",
                    "gomacraft_utility.settings.search", "gomacraft_utility.settings.unavailable",
                    "gomacraft_utility.settings.failed", "gomacraft_utility.settings.suspended")) {
                if (!json.has(key)) {
                    problems.add(name + ": 画面の語の訳が無い: " + key);
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
