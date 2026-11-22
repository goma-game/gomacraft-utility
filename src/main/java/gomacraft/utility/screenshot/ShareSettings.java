package gomacraft.utility.screenshot;

import gomacraft.utility.screenshot.ScreenshotRules.Target;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 各自の手元の、スクショの投稿の設定({@code config/gomacraft_utility-screenshot.toml})。サーバの宣言ではない。
 *
 * <ul>
 *   <li>{@code default_target}: 普通に押したときの行き先({@code chat} / {@code screenshot} / {@code both})。既定は chat。
 *   <li>{@code chooser_modifier}: 押しながら押すと行き先を選ぶ画面を出す修飾キー({@code ctrl} / {@code shift} /
 *       {@code alt})。既定は ctrl。
 * </ul>
 *
 * <p>知らない値は既定に倒し、警告に出す(黙って別の行き先へ送らない —— 既定は chat で、どちらも本人の名義)。
 */
public record ShareSettings(Target defaultTarget, Modifier chooserModifier, List<String> problems) {
    public static final String FILE_NAME = "gomacraft_utility-screenshot.toml";

    private static final Pattern ENTRY = Pattern.compile("([a-z_]+)\\s*=\\s*\"?([A-Za-z]*)\"?\\s*(#.*)?");

    /** 選ぶ画面を出す修飾キー。 */
    public enum Modifier {
        CTRL,
        SHIFT,
        ALT;

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
                        problems.add("chooser_modifier の値が違う(ctrl / shift / alt): " + value);
                    }
                }
                default -> problems.add("知らない設定: " + m.group(1));
            }
        }
        return new ShareSettings(target, modifier, List.copyOf(problems));
    }

    /** 読む。無ければ既定の値で作ってから読む(在るファイルは書き換えない)。 */
    public static ShareSettings load(Path configDir) throws IOException {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            Files.createDirectories(configDir);
            Files.writeString(file, defaultText(), StandardCharsets.UTF_8);
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static String defaultText() {
        return """
                # gomacraft_utility のスクショの投稿の設定(各自の手元の設定)。
                # default_target: 普通に「Discord に投稿」を押したときの行き先。
                #   chat = そのサーバのチャットのチャンネル / screenshot = スクショのチャンネル / both = 両方
                #   (チャットのチャンネルが無いサーバでは、chat もスクショのチャンネルへ出る)
                default_target = "chat"
                # chooser_modifier: これを押しながら押すと、行き先を選ぶ画面が出る(ctrl / shift / alt)。
                chooser_modifier = "ctrl"
                """;
    }
}
