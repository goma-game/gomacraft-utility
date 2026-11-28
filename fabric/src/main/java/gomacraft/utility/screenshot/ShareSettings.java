package gomacraft.utility.screenshot;

import gomacraft.utility.screenshot.ScreenshotRules.Target;
import gomacraft.utility.settings.ClientSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 各自の手元の、スクショの投稿の設定(手元の設定 {@code gomacraft_utility-client.toml} の {@code [screenshot_share]})。
 * サーバの宣言ではない。
 *
 * <ul>
 *   <li>{@code default_target}: 普通に押したときの行き先({@code chat} / {@code screenshot} / {@code both})。既定は chat。
 *   <li>{@code chooser_modifier}: 押しながら押すと行き先を選ぶ画面を出す修飾キー({@code ctrl} /
 *       {@code alt})。既定は ctrl。
 * </ul>
 *
 * <p>知らない値は既定に倒し、警告に出す(黙って別の行き先へ送らない —— 既定は chat で、どちらも本人の名義)。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスから写し、前の置き場(forge 版の古い版のファイル)からの移し替えを除いた
 * (fabric 版には前の置き場が無い)。
 */
public record ShareSettings(Target defaultTarget, Modifier chooserModifier, List<String> problems) {
    private static final Pattern ENTRY = Pattern.compile("([a-z_]+)\\s*=\\s*\"?([A-Za-z]*)\"?\\s*(#.*)?");

    /**
     * 選ぶ画面を出す修飾キー。⚠ Shift は選べない: バニラの {@code Screen#handleComponentClicked} は
     * Shift を押している間 ClickEvent を飛ばして挿入の文字を入れるだけなので、押しても何も起きない
     * (1.21.1 の実物で確かめた。26.2 では確かめていない)。
     */
    public enum Modifier {
        CTRL("Ctrl"),
        ALT("Alt");

        /** 説明の文に出す名前。 */
        public final String label;

        Modifier(String label) {
            this.label = label;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static ShareSettings defaults() {
        return new ShareSettings(Target.CHAT, Modifier.CTRL, List.of());
    }

    public static ShareSettings parse(String text) {
        Target target = Target.CHAT;
        Modifier modifier = Modifier.CTRL;
        List<String> problems = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                continue;
            }
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) {
                problems.add("読めない行: " + line);
                continue;
            }
            String value = m.group(2).toLowerCase(Locale.ROOT);
            switch (m.group(1)) {
                case "default_target" -> {
                    var parsed = Target.parse(value);
                    if (parsed.isPresent()) {
                        target = parsed.get();
                    } else {
                        problems.add("default_target の値が違う(chat / screenshot / both): " + value);
                    }
                }
                case "chooser_modifier" -> {
                    Modifier found = null;
                    for (Modifier candidate : Modifier.values()) {
                        if (candidate.key().equals(value)) {
                            found = candidate;
                        }
                    }
                    if (found != null) {
                        modifier = found;
                    } else {
                        problems.add("chooser_modifier の値が違う(ctrl / alt): " + value);
                    }
                }
                default -> problems.add("知らない設定: " + m.group(1));
            }
        }
        return new ShareSettings(target, modifier, List.copyOf(problems));
    }

    /** 手元の設定({@code gomacraft_utility-client.toml} の {@code [screenshot_share]})の項目。設定の画面にも並ぶ。 */
    public static final ClientSettings.Choice DEFAULT_TARGET = new ClientSettings.Choice(
            ScreenshotFeatures.SHARE, "default_target", "chat", List.of("chat", "screenshot", "both"));
    public static final ClientSettings.Choice CHOOSER_MODIFIER = new ClientSettings.Choice(
            ScreenshotFeatures.SHARE, "chooser_modifier", "ctrl", List.of("ctrl", "alt"));

    /** 手元の設定の今の値から作る(値は Choice が取りうる語に限られている)。 */
    public static ShareSettings from(ClientSettings settings) {
        return parse("default_target = \"" + settings.get(DEFAULT_TARGET) + "\"\n"
                + "chooser_modifier = \"" + settings.get(CHOOSER_MODIFIER) + "\"\n");
    }
}
