package gomacraft.utility.settings;

import gomacraft.utility.feature.TomlLines;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 各自の手元の、機能ごとの設定({@code config/gomacraft_utility-client.toml})。サーバの宣言ではない。
 *
 * <p>機能は {@link #register} で設定の項目を宣言する(名前・既定の値・取りうる値)。値は {@code [<機能>]} の節の
 * {@code <名前> = <値>} で持つ。設定の画面は、宣言された項目を全部並べる(機能を足せば画面にも並ぶ)。
 *
 * <ul>
 *   <li>書いていない項目は既定の値。読めない値は既定の値に倒し、{@link #problems} に出す(黙って別の動きにしない)。</li>
 *   <li>知らない節・名前の行は残す(書き戻すときに消さない —— 別の版の Mod が持つ項目かもしれない)。</li>
 * </ul>
 *
 * <p>Minecraft に依らない(試験はここを見る)。
 */
public final class ClientSettings {
    public static final String FILE_NAME = "gomacraft_utility-client.toml";

    private static final Pattern SECTION = Pattern.compile("\\[\\s*([a-z0-9_]+)\\s*]\\s*(#.*)?");
    private static final Pattern ENTRY = Pattern.compile("([a-z0-9_]+)\\s*=\\s*(\"[^\"]*\"|[^#\\s]+)\\s*(#.*)?");

    /** 設定の項目の宣言。 */
    public sealed interface Setting<T> permits Bool, Int, Choice {
        /** 機能の名前(節の名前)。 */
        String feature();

        /** 項目の名前(節の中の名前)。{@code [a-z0-9_]+}。 */
        String key();

        T defaultValue();

        /** 書かれた文字列を値にする。読めなければ空。 */
        Optional<T> parse(String raw);

        /** 値を書く文字列にする。 */
        String format(T value);

        /** 画面に出す名前の訳の鍵。 */
        default String translationKey() {
            return "gomacraft_utility.settings." + feature() + "." + key();
        }
    }

    /** 入り切り。 */
    public record Bool(String feature, String key, Boolean defaultValue) implements Setting<Boolean> {
        @Override
        public Optional<Boolean> parse(String raw) {
            return switch (raw) {
                case "true" -> Optional.of(true);
                case "false" -> Optional.of(false);
                default -> Optional.empty();
            };
        }

        @Override
        public String format(Boolean value) {
            return value.toString();
        }
    }

    /** 範囲の決まった整数。 */
    public record Int(String feature, String key, Integer defaultValue, int min, int max) implements Setting<Integer> {
        @Override
        public Optional<Integer> parse(String raw) {
            try {
                int v = Integer.parseInt(raw);
                return v >= min && v <= max ? Optional.of(v) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        @Override
        public String format(Integer value) {
            return value.toString();
        }
    }

    /** 決まった語のどれか(英小文字。書くときは引用符で囲む)。 */
    public record Choice(String feature, String key, String defaultValue, List<String> choices)
            implements Setting<String> {
        @Override
        public Optional<String> parse(String raw) {
            String v = raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2
                    ? raw.substring(1, raw.length() - 1) : raw;
            v = v.toLowerCase(Locale.ROOT);
            return choices.contains(v) ? Optional.of(v) : Optional.empty();
        }

        @Override
        public String format(String value) {
            return "\"" + value + "\"";
        }
    }

    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    /** 節 → 名前 → 書かれた文字列(知らない項目も含めて、読んだまま持つ)。 */
    private final Map<String, Map<String, String>> raw = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Path file;
    /** 読んだ中身(書き戻すときは、この上で値の行だけを替える)。まだ無ければ null。 */
    private String original;
    /** ファイルを読めなかった(書き戻しを断る)。 */
    private boolean unreadable;

    /**
     * 値が変わり得たとき(読んだ後・書き戻した後)に呼ぶもの。機能が値を自分の形へ写すのに使う。例外は飲まずに投げる
     * (呼ぶ側の機能の守り {@code Features.guard} で包んで足す)。
     */
    public synchronized void onChange(Runnable listener) {
        listeners.add(listener);
    }

    private void fire() {
        List<Runnable> copy;
        synchronized (this) {
            copy = List.copyOf(listeners);
        }
        copy.forEach(Runnable::run);
    }

    /** ファイルに値が書かれているか(既定の値のままか、を見分ける —— 前の置き場からの移し替えに使う)。 */
    public synchronized boolean isWritten(Setting<?> setting) {
        return raw.getOrDefault(setting.feature(), Map.of()).containsKey(setting.key());
    }

    /** 項目を宣言する(同じ機能・名前を 2 度宣言したら、前のものを置き換える)。 */
    public synchronized <T> Setting<T> register(Setting<T> setting) {
        settings.put(setting.feature() + "." + setting.key(), setting);
        return setting;
    }

    /** 宣言された項目(宣言した順)。 */
    public synchronized List<Setting<?>> all() {
        return List.copyOf(settings.values());
    }

    /** 今の値(書いていなければ・読めなければ既定の値)。 */
    public synchronized <T> T get(Setting<T> setting) {
        String text = raw.getOrDefault(setting.feature(), Map.of()).get(setting.key());
        if (text == null) {
            return setting.defaultValue();
        }
        return setting.parse(text).orElse(setting.defaultValue());
    }

    /** 値を変える(書き戻すのは {@link #save})。 */
    public synchronized <T> void set(Setting<T> setting, T value) {
        raw.computeIfAbsent(setting.feature(), f -> new LinkedHashMap<>()).put(setting.key(), setting.format(value));
    }

    /** 読めなかった行・値(起動のときに警告へ出す)。 */
    public synchronized List<String> problems() {
        List<String> out = new ArrayList<>(problems);
        for (Setting<?> s : settings.values()) {
            String text = raw.getOrDefault(s.feature(), Map.of()).get(s.key());
            if (text != null && s.parse(text).isEmpty()) {
                out.add(s.feature() + "." + s.key() + " の値が読めない(既定の値を使う): " + text);
            }
        }
        return out;
    }

    /** 設定の中身を読む(前に読んだ値は捨てる)。 */
    public synchronized void parse(String text) {
        raw.clear();
        problems.clear();
        original = text;
        String section = "";
        for (String line0 : text.split("\\R")) {
            String line = line0.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher sec = SECTION.matcher(line);
            if (sec.matches()) {
                section = sec.group(1);
                continue;
            }
            if (line.startsWith("[")) {
                // 形に合わない節の見出し([Other.Mod] など): 続く行を前の節の値として読まない
                section = "";
                problems.add("読めない節の見出し: " + line);
                continue;
            }
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches() && !section.isEmpty()) {
                raw.computeIfAbsent(section, f -> new LinkedHashMap<>()).put(entry.group(1), entry.group(2));
            } else {
                problems.add("読めない行: " + line);
            }
        }
    }

    /**
     * 中身を書く文字列。読んだ中身の上で、宣言された項目の行の値だけを替える(手で書いたコメント・知らない項目・
     * 読めない行は残す —— {@link TomlLines})。無い項目は既定の値も含めて足す。初めて作るときは頭に説明を置く。
     */
    public synchronized String render() {
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        for (Setting<?> s : settings.values()) {
            values.computeIfAbsent(s.feature(), f -> new LinkedHashMap<>()).put(s.key(), current(s));
        }
        String base = original != null ? original
                : "# gomacraft_utility の手元の設定(機能ごとの節)。ゲームの中の設定の画面からも変えられる。\n"
                        + "# 読めない値は既定の値を使う。\n";
        return TomlLines.update(base, values);
    }

    private <T> String current(Setting<T> setting) {
        return setting.format(get(setting));
    }

    /** 設定のファイルを読む(無ければ既定の値で作る)。読めなければ既定の値のまま、理由を {@code warn} へ。 */
    public synchronized void load(Path configDir, Consumer<String> warn) {
        file = configDir.resolve(FILE_NAME);
        boolean exists = Files.exists(file);
        unreadable = false;
        try {
            if (exists) {
                parse(Files.readString(file, StandardCharsets.UTF_8));
            } else {
                raw.clear();
                original = null;
            }
        } catch (IOException e) {
            // UTF-8 でない・読めない: 既定の値で動くが、書き戻さない(人が書いた中身を既定の値で消さない)
            unreadable = true;
            raw.clear();
            warn.accept("gomacraft_utility: 手元の設定を読めない(既定の値で動き、ファイルは書き換えない): " + e);
        }
        problems().forEach(p -> warn.accept("gomacraft_utility: " + p));
        // 機能が値を写す(前の置き場からの移し替えもここ。既定の値でファイルを作るのはその後)
        fire();
        if (!exists) {
            try {
                save();
            } catch (IOException e) {
                warn.accept("gomacraft_utility: 手元の設定を書けない: " + e);
            }
        }
    }

    /** 書き戻す({@link #load} の前は何もしない)。書いたら {@link #onChange} の listener を呼ぶ。 */
    public synchronized void save() throws IOException {
        if (file == null) {
            return;
        }
        if (unreadable) {
            throw new IOException(file + " を読めなかったので、書き戻さない(ファイルを直してから起動し直す)");
        }
        String text = render();
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        original = text;
        fire();
    }
}
