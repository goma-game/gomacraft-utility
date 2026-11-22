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
    private final boolean unreadable;

    private FeatureConfig(Map<String, String> values, boolean unreadable) {
        this.values = values;
        this.unreadable = unreadable;
    }

    public static FeatureConfig empty() {
        return new FeatureConfig(Map.of(), false);
    }

    /** 設定のファイルを読めなかった(全部の機能を切る)。 */
    public static FeatureConfig unreadable() {
        return new FeatureConfig(Map.of(), true);
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
        return new FeatureConfig(values, false);
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

    /**
     * 設定の中身の {@code [features]} の値を書き換えた文字列(設定の画面が使う)。在る行は値だけを替え(コメントと
     * 並びは残す)、無い名前は {@code [features]} の最後に足す。{@code [features]} が無ければ足す。
     */
    public static String update(String text, Map<String, Boolean> changes) {
        List<String> lines = new java.util.ArrayList<>(List.of(text.split("\\R", -1)));
        Map<String, Boolean> rest = new LinkedHashMap<>(changes);
        String section = "";
        int endOfFeatures = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            Matcher sec = SECTION.matcher(line);
            if (sec.matches()) {
                section = sec.group(1);
                continue;
            }
            if (!section.equals("features")) {
                continue;
            }
            if (!line.isEmpty() && !line.startsWith("#")) {
                endOfFeatures = i;
            }
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches() && rest.containsKey(entry.group(1))) {
                String comment = entry.group(3) == null ? "" : "  " + entry.group(3);
                lines.set(i, entry.group(1) + " = " + rest.remove(entry.group(1)) + comment);
            }
        }
        if (!rest.isEmpty()) {
            List<String> added = new java.util.ArrayList<>();
            rest.forEach((id, on) -> added.add(id + " = " + on));
            if (endOfFeatures < 0) {
                if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                    lines.remove(lines.size() - 1);
                }
                lines.add("[features]");
                lines.addAll(added);
                lines.add("");
            } else {
                lines.addAll(endOfFeatures + 1, added);
            }
        }
        return String.join("\n", lines);
    }

    /** 設定のファイルの値を書き換える(無ければ既定の中身から作る)。効くのは次の起動から(mixin の当否は起動で決まる)。 */
    public static void write(Path configDir, List<? extends Feature> features, Map<String, Boolean> changes)
            throws IOException {
        Path file = configDir.resolve(FILE_NAME);
        String text = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : defaults(features);
        Files.createDirectories(configDir);
        Files.writeString(file, update(text, changes), StandardCharsets.UTF_8);
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
