package gomacraft.utility.chatimage.client;

import com.mojang.blaze3d.platform.NativeImage;
import gomacraft.utility.chatimage.DiscordImageUrl;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * 画像を取りにいって、絵(NativeImage)にする。主スレッドの外(専用の 2 本のスレッド)で呼ぶ。
 *
 * <p>形の決め方: 取りにいく先は {@code media.discordapp.net}({@link DiscordImageUrl#fetchUri})。webp は Java の
 * ImageIO で読めないので、webp と gif だけ {@code format=png} で Discord の側で変えてもらう(png・jpg はそのまま。
 * 写真を PNG に変えると上限を超えやすい)。受けたものは形によらず ImageIO で読む({@code NativeImage.read} は PNG の頭を確かめて、それ以外を
 * 断るため。PNG・JPEG・GIF なら ImageIO で読める)。読む前に頭の幅と高さを見て大きすぎるものを断り、大きい絵は
 * 間引いて読む(長い辺を {@link #MAX_SIDE} 以下にする)。読んだ点を NativeImage へ写す(PNG へ作り直さない)。
 *
 * <p>⚠ 転送(redirect)は追わない(ほかの置き場へ行かせない)。3xx は失敗、404・403・410 は失効と見る。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスから写した。違うのは点を書く口の名前だけ(26.x の
 * {@code NativeImage#setPixelABGR}。1.21.1 の {@code setPixelRGBA} と同じく ABGR の並びを取る)。
 */
final class ImageFetcher {
    /** 受け取る大きさの上限。 */
    static final int MAX_BYTES = 16 * 1024 * 1024;
    /** 読む形(ImageIO の読み手の名前。webp は Discord の側で PNG に変えてもらう)。 */
    private static final java.util.Set<String> FORMATS = java.util.Set.of("png", "jpeg", "jpg", "gif");
    /** 元の絵の幅・高さの上限(これより大きいものは読まない)。 */
    static final int MAX_SOURCE_SIDE = 16384;
    static final long MAX_SOURCE_PIXELS = 60_000_000L;
    /** 絵にしたときの長い辺の上限(間引いて読む)。 */
    static final int MAX_SIDE = 2048;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 頭が返るまでの上限。中身を読み切るまでの上限は {@link #READ_DEADLINE_MS}。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long READ_DEADLINE_MS = 20_000;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "gomacraft_utility chat image watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /** 失敗の種類。 */
    enum Failure {
        EXPIRED, TOO_LARGE, FAILED
    }

    static final class FetchException extends Exception {
        final Failure failure;

        FetchException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }
    }

    private ImageFetcher() {
    }

    static NativeImage fetch(DiscordImageUrl url) throws FetchException {
        if (url.expiredAt(System.currentTimeMillis() / 1000)) {
            throw new FetchException(Failure.EXPIRED, "ex passed");
        }
        byte[] data = download(url);
        if (Thread.currentThread().isInterrupted()) {
            throw new FetchException(Failure.FAILED, "cancelled");
        }
        return decode(data);
    }

    private static byte[] download(DiscordImageUrl url) throws FetchException {
        URI uri = url.fetchUri();
        // ⚠ 取りにいく先は DiscordImageUrl が組んだものだけ(ここでも置き場を確かめ直す)
        if (!"https".equals(uri.getScheme()) || !"media.discordapp.net".equals(uri.getHost())) {
            throw new FetchException(Failure.FAILED, "refused host");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "gomacraft-utility (Minecraft mod)")
                .header("Accept", "image/*")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new FetchException(Failure.FAILED, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException(Failure.FAILED, "interrupted");
        }
        try (InputStream body = response.body()) {
            int status = response.statusCode();
            // 403 は、期限(ex)を過ぎているときだけ失効と見る(ほかの 403 は失敗)
            if (status == 404 || status == 410
                    || (status == 403 && url.expiredAt(System.currentTimeMillis() / 1000))) {
                throw new FetchException(Failure.EXPIRED, "status " + status);
            }
            if (status != 200) {
                throw new FetchException(Failure.FAILED, "status " + status);
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declared > MAX_BYTES) {
                throw new FetchException(Failure.TOO_LARGE, "content-length " + declared);
            }
            // ⚠ Content-Length を信じず、読んだ量を数える。頭の後の読み込みにも期限を置く(止まった読み込みは、
            // 期限で流れを閉じて起こす —— 頭の期限 REQUEST_TIMEOUT は中身の読み込みに効かない)
            long deadline = System.currentTimeMillis() + READ_DEADLINE_MS;
            ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
                try {
                    body.close();
                } catch (IOException ignored) {
                    // 閉じられなくても、読み込みの側が期限を見る
                }
            }, READ_DEADLINE_MS, TimeUnit.MILLISECONDS);
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = body.read(buffer)) >= 0) {
                    if (out.size() + n > MAX_BYTES) {
                        throw new FetchException(Failure.TOO_LARGE, "body over " + MAX_BYTES);
                    }
                    if (System.currentTimeMillis() > deadline) {
                        throw new FetchException(Failure.FAILED, "read timeout");
                    }
                    out.write(buffer, 0, n);
                }
                if (System.currentTimeMillis() > deadline) {
                    throw new FetchException(Failure.FAILED, "read timeout");
                }
                return out.toByteArray();
            } finally {
                watchdog.cancel(false);
            }
        } catch (IOException e) {
            throw new FetchException(Failure.FAILED, e.toString());
        }
    }

    private static NativeImage decode(byte[] data) throws FetchException {
        // 手元の記憶の上で読む(ImageIO の既定は、場合によって一時ファイルへ書き出す)
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            while (readers.hasNext()) {
                ImageReader candidate = readers.next();
                // ⚠ 読み手は png・jpeg・gif に限る(名前が .png の TIFF・BMP などを読ませない)
                String format = candidate.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (reader == null && FORMATS.contains(format)) {
                    reader = candidate;
                } else {
                    candidate.dispose();
                }
            }
            if (reader == null) {
                throw new FetchException(Failure.FAILED, "unsupported image format");
            }
            try {
                reader.setInput(stream, true, true);
                // ⚠ 読む前に頭の大きさを見る(小さなファイルでも、広げると大きな絵になりうる)
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE
                        || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new FetchException(Failure.TOO_LARGE, width + "x" + height);
                }
                int step = 1;
                // 間引いた後の辺は切り上げ(4097 を 2 で間引くと 2049)
                while ((Math.max(width, height) + step - 1) / step > MAX_SIDE) {
                    step++;
                }
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                return toNative(image);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw new FetchException(Failure.FAILED, e.toString());
        }
    }

    private static NativeImage toNative(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        try {
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                image.getRGB(0, y, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) {
                    int argb = row[x];
                    // ARGB → ABGR(NativeImage の並び)
                    int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                    out.setPixelABGR(x, y, abgr);
                }
            }
            return out;
        } catch (RuntimeException e) {
            out.close();
            throw e;
        }
    }
}
