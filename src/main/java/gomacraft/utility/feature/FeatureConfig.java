package gomacraft.utility.feature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 機能ごとの切り替えの設定({@code config/gomacraft_utility-features.toml})。
 *
 * <p>NeoForge の設定の仕組み(ModConfigSpec)を使わずに自分で読むのは、mixin の当否を決める時点
 * (Mod の組み立てより前)で値が要るため。読むのは {@code [features]} の {@code 名前 = true|false} だけ。
 * 書いていない機能は既定の値を使い、読めない値の機能は切る(壊れた設定で黙って有効にしない)。
 */
public final class FeatureConfig {
    public static final String FILE_NAME = "gomacraft_utility-features.toml";

    private static final Pattern SECTION = Pattern.compile("\\[\\s*([^\\]]*?)\\s*]\\s*(#.*)?");
    private static final Pattern ENTRY = Pattern.compile("([A-Za-z0-9_\\-]+)\\s*=\\s*([^#]*?)\\s*(#.*)?");

    /** 機能の名前 → 書かれていた値(true / false 以外も、そのまま持つ)。 */
    private final Map<String, String> values;

    private FeatureConfig(Map<String, String> values) {
        this.values = values;
    }

    public static FeatureConfig empty() {
        return new FeatureConfig(Map.of());
    }

    /** 設定の中身を読む。{@code [features]} の外と、知らない形の行は無視する。 */
    public static FeatureConfig parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
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
            if (!section.equals("features")) {
                continue;
            }
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches()) {
                values.put(entry.group(1), entry.group(2));
            }
        }
        return new FeatureConfig(values);
    }

    /**
     * 設定のファイルを読む。無ければ既定の値で作ってから読む(作れなければ既定の値で動く)。
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
        return sb.toString();
    }

    /** 書かれた値。書かれていなければ空。値が true / false でなければ {@link Value#INVALID}。 */
    public Optional<Value> value(String id) {
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

    public enum Value {
        TRUE,
        FALSE,
        INVALID
    }
}
