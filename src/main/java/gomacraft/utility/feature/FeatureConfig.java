package gomacraft.utility.feature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 機能ごとの切り替えの設定({@code config/gomacraft_utility-features.toml})。
 *
 * <p>NeoForge の設定の仕組み(ModConfigSpec)を使わずに自分で読むのは、mixin の当否を決める時点
 * (Mod の組み立てより前)で値が要るため。読むのは {@code [features]} の {@code 名前 = true|false} だけ。
 * 書いていない機能は既定の値を使い、読めない値の機能は切る(壊れた設定で黙って有効にしない)。
 * 設定のファイルそのものを読めなければ、全部の機能を切る({@link #unreadable})。
 */
public final class FeatureConfig {
    public static final String FILE_NAME = "gomacraft_utility-features.toml";

    private static final Pattern SECTION = Pattern.compile("\\[\\s*([^\\]]*?)\\s*]\\s*(#.*)?");
    private static final Pattern ENTRY = Pattern.compile("([A-Za-z0-9_\\-]+)\\s*=\\s*([^#]*?)\\s*(#.*)?");

    /** 機能の名前 → 書かれていた値(true / false 以外も、そのまま持つ)。 */
    private final Map<String, String> values;
    /** {@code [settings]} の、機能の中の細かい設定(件数など)の名前 → 書かれていた値。 */
    private final Map<String, String> settings;
    private final boolean unreadable;

    private FeatureConfig(Map<String, String> values, Map<String, String> settings, boolean unreadable) {
        this.values = values;
        this.settings = settings;
        this.unreadable = unreadable;
    }

    public static FeatureConfig empty() {
        return new FeatureConfig(Map.of(), Map.of(), false);
    }

    /** 設定のファイルを読めなかった(全部の機能を切る)。 */
    public static FeatureConfig unreadable() {
        return new FeatureConfig(Map.of(), Map.of(), true);
    }

    /**
     * 設定の中身を読む。{@code [features]}(機能の入り切り)と {@code [settings]}(機能の中の細かい設定)の外と、
     * 知らない形の行は無視する。
     */
    public static FeatureConfig parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> settings = new LinkedHashMap<>();
        String section = "";
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher sec = SECTION.matcher(line);
            if (sec.matches()) {
                section = sec.group(1);
                continue;
            }
            Map<String, String> target = switch (section) {
                case "features" -> values;
                case "settings" -> settings;
                default -> null;
            };
            if (target == null) {
                continue;
            }
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches()) {
                target.put(entry.group(1), entry.group(2));
            }
        }
        return new FeatureConfig(values, settings, false);
    }

    /** {@code [settings]} に書かれた値(書かれていない・設定のファイルを読めないなら空。値の読み方は使う側が決める)。 */
    public Optional<String> setting(String key) {
        return unreadable ? Optional.empty() : Optional.ofNullable(settings.get(key));
    }

    /**
     * 設定のファイルを読む。無ければ既定の値で作ってから読む。
     * 在るファイルは書き換えない(人が書いた値を消さない)。
     */
    public static FeatureConfig load(Path configDir, List<? extends Feature> features) throws IOException {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            Files.createDirectories(configDir);
            Files.writeString(file, defaults(features), StandardCharsets.UTF_8);
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** 既定の値の設定ファイルの中身。 */
    public static String defaults(List<? extends Feature> features) {
        StringBuilder sb = new StringBuilder();
        sb.append("# gomacraft_utility の機能ごとの切り替え(true = 使う / false = 切る)。\n");
        sb.append("# 書いていない機能は既定の値を使う。値が true / false でない機能は切る。\n");
        sb.append("# 変えたらゲームを起動し直す(mixin の当否は起動のときに決まる)。\n");
        sb.append("[features]\n");
        for (Feature f : features) {
            sb.append(f.id()).append(" = ").append(f.enabledByDefault()).append('\n');
        }
        List<Setting> all = features.stream().flatMap(f -> f.settings().stream()).toList();
        if (!all.isEmpty()) {
            sb.append("\n# 機能の中の細かい設定。書いていない・読めない値は既定の値を使う。\n");
            sb.append("[settings]\n");
            for (Setting s : all) {
                sb.append("# ").append(s.comment()).append('\n');
                sb.append(s.key()).append(" = ").append(s.defaultValue()).append('\n');
            }
        }
        return sb.toString();
    }

    /** 書かれた値。書かれていなければ空。値が true / false でなければ {@link Value#INVALID}。 */
    public Optional<Value> value(String id) {
        if (unreadable) {
            return Optional.of(Value.UNREADABLE);
        }
        String v = values.get(id);
        if (v == null) {
            return Optional.empty();
        }
        return Optional.of(switch (v) {
            case "true" -> Value.TRUE;
            case "false" -> Value.FALSE;
            default -> Value.INVALID;
        });
    }

    /** 機能の一覧に無い名前(書き間違いを黙って無視しないために、起動のときに警告へ出す)。 */
    public Set<String> unknownKeys(List<? extends Feature> features) {
        Set<String> known = features.stream().map(Feature::id).collect(Collectors.toSet());
        return values.keySet().stream().filter(k -> !known.contains(k))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public enum Value {
        TRUE,
        FALSE,
        INVALID,
        UNREADABLE
    }
}
