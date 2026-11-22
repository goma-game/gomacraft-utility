package gomacraft.utility.search;

import java.util.function.IntFunction;

/**
 * 訳の文の書式({@code %s}・{@code %1$s}・{@code %%})に引数を埋める。Minecraft の訳の書式と同じ読み方をする
 * (Minecraft に依らないので、試験はここを見る)。
 */
public final class EnglishFormat {
    private EnglishFormat() {
    }

    /**
     * @param template 訳の文
     * @param args     引数の番号(0 から)→ 文字列。範囲外の番号には呼ばれない
     * @param argCount 引数の数
     */
    public static String format(String template, IntFunction<String> args, int argCount) {
        StringBuilder out = new StringBuilder(template.length() + 16);
        int next = 0;
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c != '%' || i + 1 >= n) {
                out.append(c);
                i++;
                continue;
            }
            char d = template.charAt(i + 1);
            if (d == '%') {
                out.append('%');
                i += 2;
                continue;
            }
            if (d == 's') {
                if (next < argCount) {
                    out.append(args.apply(next));
                }
                next++;
                i += 2;
                continue;
            }
            int j = i + 1;
            while (j < n && Character.isDigit(template.charAt(j))) {
                j++;
            }
            if (j > i + 1 && j - i - 1 <= 3 && j + 1 < n && template.charAt(j) == '$' && template.charAt(j + 1) == 's') {
                int index = Integer.parseInt(template.substring(i + 1, j)) - 1;
                if (index >= 0 && index < argCount) {
                    out.append(args.apply(index));
                }
                i = j + 2;
                continue;
            }
            // 知らない書式はそのまま残す
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
