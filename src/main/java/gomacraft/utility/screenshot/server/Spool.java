package gomacraft.utility.screenshot.server;

import com.google.gson.JsonObject;
import gomacraft.utility.screenshot.ScreenshotRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

/**
 * サーバの置き場(ゲームのフォルダの {@code gomacraft_utility/screenshots/})。1 枚は画像と、メタの {@code .json} の
 * 2 つ。⚠ {@code .json} を最後に置く —— 在れば揃っている印(取り出す側はこれを見て拾う)。
 *
 * <p>⚠ 形は gomabase の取り出しの helper({@code mc-screenshot-take})との約束(goma-org/gomabase#2295)。
 */
public final class Spool {
    private Spool() {
    }

    public static Path dir(Path gameDir) {
        return gameDir.resolve("gomacraft_utility").resolve("screenshots");
    }

    /** 上限に達しているか(数と大きさ。途中の一時ファイルも数える)。置き場が無ければ空。 */
    public static boolean full(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        int files = 0;
        long bytes = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && !p.getFileName().toString().endsWith(".json")) {
                    files++;
                    bytes += Files.size(p);
                }
            }
        }
        return files >= ScreenshotRules.SPOOL_MAX_FILES || bytes >= ScreenshotRules.SPOOL_MAX_BYTES;
    }

    /** 1 枚を書く(一時ファイル → rename、画像 → メタの順)。置いた名前(拡張子の前)を返す。 */
    public static String write(Path dir, byte[] image, ScreenshotRules.Format format, String playerName,
            UUID playerId, Instant now, int random) throws IOException {
        Files.createDirectories(dir);
        String stem = ScreenshotRules.stem(now, playerId, random);
        JsonObject meta = new JsonObject();
        meta.addProperty("player", playerName);
        meta.addProperty("uuid", playerId.toString());
        meta.addProperty("taken_at", now.toString());
        meta.addProperty("format", format.extension);
        place(dir, stem + "." + format.extension, image);
        place(dir, stem + ".json", meta.toString().getBytes(StandardCharsets.UTF_8));
        return stem;
    }

    private static void place(Path dir, String name, byte[] content) throws IOException {
        Path tmp = dir.resolve("." + name + ".tmp");
        Files.write(tmp, content);
        Files.move(tmp, dir.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
