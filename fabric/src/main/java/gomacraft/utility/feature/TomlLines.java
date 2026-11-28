package gomacraft.utility.feature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 設定のファイル({@code [節]} と {@code 名前 = 値} の行)の値だけを替える(設定の画面の書き戻しが使う)。
 *
 * <ul>
 *   <li>在る行は値だけを替える(行の後ろのコメント・ほかの行・並び・読めない行はそのまま残す)。同じ名前の行が
 *       同じ節に 2 つ以上あれば、全部を替える。</li>
 *   <li>無い名前は、その節の最後の行の後に足す。節が無ければ、ファイルの最後に節ごと足す。</li>
 * </ul>
 *
 * <p>Minecraft に依らない(試験はここを見る)。
 */
public final class TomlLines {
    private static final Pattern SECTION = Pattern.compile("\\[\\s*([^\\]]*?)\\s*]\\s*(#.*)?");
    private static final Pattern ENTRY = Pattern.compile("([A-Za-z0-9_\\-]+)\\s*=\\s*(\"[^\"]*\"|[^#]*?)\\s*(#.*)?");

    private TomlLines() {
    }

    /** @param values 節 → 名前 → 書く値(書く形のまま。引用符も含める) */
    public static String update(String text, Map<String, Map<String, String>> values) {
        List<String> lines = new ArrayList<>(List.of(text.split("\\R", -1)));
        // 節 → 名前を足す位置(その節の見出しか、最後の中身の行の次)
        Map<String, Integer> insertAt = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        String section = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            Matcher sec = SECTION.matcher(line);
            if (sec.matches()) {
                section = sec.group(1);
                insertAt.putIfAbsent(section, i + 1);
                continue;
            }
            if (section == null || line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            insertAt.put(section, i + 1);
            Matcher entry = ENTRY.matcher(line);
            Map<String, String> wanted = values.get(section);
            if (entry.matches() && wanted != null && wanted.containsKey(entry.group(1))) {
                String comment = entry.group(3) == null ? "" : "  " + entry.group(3);
                lines.set(i, entry.group(1) + " = " + wanted.get(entry.group(1)) + comment);
                seen.add(section + "\0" + entry.group(1));
            }
        }
        // 足す分は後ろの位置から入れる(前の位置がずれないように)
        List<Map.Entry<Integer, List<String>>> inserts = new ArrayList<>();
        List<String> newSections = new ArrayList<>();
        values.forEach((sec, entries) -> {
            List<String> add = new ArrayList<>();
            entries.forEach((k, v) -> {
                if (!seen.contains(sec + "\0" + k)) {
                    add.add(k + " = " + v);
                }
            });
            if (add.isEmpty()) {
                return;
            }
            Integer at = insertAt.get(sec);
            if (at == null) {
                newSections.add("");
                newSections.add("[" + sec + "]");
                newSections.addAll(add);
            } else {
                inserts.add(Map.entry(at, add));
            }
        });
        inserts.sort((a, b) -> Integer.compare(b.getKey(), a.getKey()));
        for (Map.Entry<Integer, List<String>> insert : inserts) {
            lines.addAll(insert.getKey(), insert.getValue());
        }
        if (!newSections.isEmpty()) {
            // 終わりの空の行の前に足す(ファイルの終わりの改行を保つ)
            int end = lines.size();
            boolean trailingNewline = end > 0 && lines.get(end - 1).isEmpty();
            if (trailingNewline) {
                lines.remove(end - 1);
            }
            if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                newSections.remove(0);
            }
            lines.addAll(newSections);
            lines.add("");
        }
        return String.join("\n", lines);
    }
}
