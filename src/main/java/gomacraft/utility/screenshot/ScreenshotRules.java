package gomacraft.utility.screenshot;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * スクショの送りの決まり(上限・画像の形・置き場の名前)。Minecraft に依らないので、JUnit で確かめる。
 *
 * <p>⚠ ここの上限と名前の形は、gomabase の取り出しの helper({@code mc-screenshot-take})と gomacraft-manager の
 * 読み手との約束。変えるときは 3 つを同時に変える(goma-org/gomabase#2295)。
 */
public final class ScreenshotRules {
    /** 1 枚の上限。Discord の無料のギルドの上限(10 MiB)の下に置く。 */
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    /** 1 個の切れ端の大きさ。バニラの serverbound の payload の上限(32767)の下に置く。 */
    public static final int CHUNK_BYTES = 30_000;
    /** 1 枚の切れ端の数の上限。 */
    public static final int MAX_CHUNKS = (MAX_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES;
    /** 幅と高さの上限(ヘッダで見る)。 */
    public static final int MAX_SIDE = 8192;
    /** 置き場の上限(manager が止まっていても溜まり続けない)。 */
    public static final int SPOOL_MAX_FILES = 20;
    public static final long SPOOL_MAX_BYTES = 64L * 1024 * 1024;
    /** 1 人が次の 1 枚を送れるまでの間(ミリ秒)。 */
    public static final long COOLDOWN_MILLIS = 30_000;
    /** 揃わない送りを捨てるまでの間(ミリ秒)。 */
    public static final long TIMEOUT_MILLIS = 60_000;
    /** サーバ全体で同時に受ける送りの数。 */
    public static final int MAX_IN_FLIGHT = 2;

    /** 置き場のファイルの名前(拡張子の前)。gomabase の helper が同じ形で縛る。 */
    public static final Pattern STEM = Pattern.compile("[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}-[0-9a-f]{8}");

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private ScreenshotRules() {
    }

    /** 画像の形。拡張子と、manager に渡す名前。 */
    public enum Format {
        PNG("png"),
        JPEG("jpg");

        public final String extension;

        Format(String extension) {
            this.extension = extension;
        }
    }

    /** 先頭のバイトで画像の形を決める。どちらでもなければ空。 */
    public static Optional<Format> sniff(byte[] head) {
        if (head.length >= 8 && (head[0] & 0xff) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 0x0d && head[5] == 0x0a && head[6] == 0x1a && head[7] == 0x0a) {
            return Optional.of(Format.PNG);
        }
        if (head.length >= 3 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8 && (head[2] & 0xff) == 0xff) {
            return Optional.of(Format.JPEG);
        }
        return Optional.empty();
    }

    /** 幅と高さ。 */
    public record Size(int width, int height) {
    }

    /**
     * ヘッダだけを読んで幅と高さを返す(画像を全部は解かない)。読めなければ空。
     * PNG は最初の IHDR、JPEG は最初の SOF のマーカーを見る。
     */
    public static Optional<Size> size(byte[] data) {
        Optional<Format> format = sniff(data);
        if (format.isEmpty()) {
            return Optional.empty();
        }
        return format.get() == Format.PNG ? pngSize(data) : jpegSize(data);
    }

    private static Optional<Size> pngSize(byte[] d) {
        // 8 バイトの印 → 長さ(4) → "IHDR"(4) → 幅(4) → 高さ(4)
        if (d.length < 24 || d[12] != 'I' || d[13] != 'H' || d[14] != 'D' || d[15] != 'R') {
            return Optional.empty();
        }
        return Optional.of(new Size(int32(d, 16), int32(d, 20)));
    }

    private static Optional<Size> jpegSize(byte[] d) {
        int i = 2;
        while (i + 4 <= d.length) {
            if ((d[i] & 0xff) != 0xff) {
                return Optional.empty();
            }
            int marker = d[i + 1] & 0xff;
            if (marker == 0xff) {
                i++;
                continue;
            }
            if (marker == 0xd8 || marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
                i += 2;
                continue;
            }
            if (marker == 0xd9 || marker == 0xda) {
                return Optional.empty();
            }
            int length = ((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
            if (length < 2) {
                return Optional.empty();
            }
            boolean sof = marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
            if (sof) {
                if (i + 9 > d.length) {
                    return Optional.empty();
                }
                int height = ((d[i + 5] & 0xff) << 8) | (d[i + 6] & 0xff);
                int width = ((d[i + 7] & 0xff) << 8) | (d[i + 8] & 0xff);
                return Optional.of(new Size(width, height));
            }
            i += 2 + length;
        }
        return Optional.empty();
    }

    private static int int32(byte[] d, int at) {
        return ((d[at] & 0xff) << 24) | ((d[at + 1] & 0xff) << 16) | ((d[at + 2] & 0xff) << 8) | (d[at + 3] & 0xff);
    }

    /** 受けてよい画像か。だめなら理由。 */
    public static Optional<Result> check(byte[] data) {
        if (data.length == 0 || data.length > MAX_BYTES) {
            return Optional.of(Result.TOO_LARGE);
        }
        Optional<Size> size = size(data);
        if (size.isEmpty()) {
            return Optional.of(Result.BAD_IMAGE);
        }
        Size s = size.get();
        if (s.width() <= 0 || s.height() <= 0 || s.width() > MAX_SIDE || s.height() > MAX_SIDE) {
            return Optional.of(Result.BAD_IMAGE);
        }
        return Optional.empty();
    }

    /** 宣言された大きさの送りを始めてよいか。 */
    public static boolean acceptableTotal(int total) {
        return total > 0 && total <= MAX_BYTES;
    }

    /** {@code total} バイトの送りの切れ端の数。 */
    public static int chunkCount(int total) {
        return (total + CHUNK_BYTES - 1) / CHUNK_BYTES;
    }

    /** {@code index} 番目の切れ端の大きさ(最後だけ短い)。 */
    public static int chunkLength(int total, int index) {
        return Math.min(CHUNK_BYTES, total - index * CHUNK_BYTES);
    }

    /** 置き場の名前(拡張子の前)を作る。時刻・本人の UUID の頭・乱数で、同じ名前にならない。 */
    public static String stem(Instant now, UUID player, int random) {
        String who = player.toString().replace("-", "").substring(0, 8);
        return STAMP.format(now) + "-" + who + "-" + HexFormat.of().toHexDigits(random);
    }

    /** 送りの結果(サーバ → クライアントの返事)。番号は通信の約束なので並びを変えない。 */
    public enum Result {
        ACCEPTED,
        OFF,
        BUSY,
        COOLDOWN,
        TOO_LARGE,
        BAD_IMAGE,
        SPOOL_FULL,
        BROKEN,
        ERROR;

        public static Result of(int ordinal) {
            Result[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ERROR;
        }

        public String key() {
            return "gomacraft_utility.screenshot.result." + name().toLowerCase(Locale.ROOT);
        }
    }
}
