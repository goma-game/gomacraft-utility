package gomacraft.utility.chatimage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * チャットの {@code open_url} の URL のうち、ゲームの中で画像として見せてよいもの(Discord の添付の画像)を選ぶ
 * (goma-game/gomacraft-utility#47)。Minecraft に依らない(試験はここを見る)。
 *
 * <p>⚠ ここが「どこへ取りにいくか」の境目。通すのは https で、置き場が {@code cdn.discordapp.com} か
 * {@code media.discordapp.net} のどちらかに完全に一致し(後ろに続く名前は通さない)、利用者の情報(userinfo)と
 * 443 以外の port を持たず、path が {@code /attachments/} で始まって画像の拡張子で終わるものだけ。拡張子は path で見る
 * (query の中の {@code .png} では通さない)。取りにいく先は、元の URL の置き場によらず {@code media.discordapp.net}
 * に組み直す({@link #fetchUri})。
 */
public final class DiscordImageUrl {
    private static final Set<String> HOSTS = Set.of("cdn.discordapp.com", "media.discordapp.net");
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    /** Discord の側で PNG に変えてもらう拡張子({@link #fetchUri})。 */
    private static final Set<String> CONVERTED = Set.of("gif", "webp");
    private static final String FETCH_HOST = "media.discordapp.net";
    /** 組み直すときに外す query(こちらで付け直す・大きさを変えさせない)。 */
    private static final Set<String> DROPPED_PARAMS = Set.of("format", "width", "height", "quality");

    private final String original;
    private final String rawPath;
    private final List<String> params;
    private final boolean convert;

    private DiscordImageUrl(String original, String rawPath, List<String> params, boolean convert) {
        this.original = original;
        this.rawPath = rawPath;
        this.params = params;
        this.convert = convert;
    }

    /** Discord の添付の画像の URL なら、その形を返す。それ以外は空(バニラの扱いのまま)。 */
    public static Optional<DiscordImageUrl> parse(String url) {
        if (url == null || url.length() > 2048) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()) {
            return Optional.empty();
        }
        String host = uri.getHost();
        if (host == null || !HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        if (uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return Optional.empty();
        }
        String rawPath = uri.getRawPath();
        if (rawPath == null || !rawPath.startsWith("/attachments/")) {
            return Optional.empty();
        }
        // ⚠ 上の段へ上がる区切りは、復号した path で断る(字面の /../ だけを見ると %2e%2e が通る)。
        // 逆斜線も復号してから見る(%5c)
        String path = uri.getPath();
        if (path == null || path.contains("\\")) {
            return Optional.empty();
        }
        for (String segment : path.split("/", -1)) {
            if (segment.equals("..") || segment.equals(".")) {
                return Optional.empty();
            }
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || path.lastIndexOf('/') > dot) {
            return Optional.empty();
        }
        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            return Optional.empty();
        }
        List<String> params = new ArrayList<>();
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                if (param.isEmpty()) {
                    continue;
                }
                int eq = param.indexOf('=');
                String name = (eq < 0 ? param : param.substring(0, eq)).toLowerCase(Locale.ROOT);
                if (!DROPPED_PARAMS.contains(name)) {
                    params.add(param);
                }
            }
        }
        return Optional.of(new DiscordImageUrl(url, rawPath, List.copyOf(params), CONVERTED.contains(extension)));
    }

    /** チャットに書かれていた元の URL(ブラウザで開くとき)。 */
    public String original() {
        return original;
    }

    /**
     * 取りにいく先。いつも {@code https://media.discordapp.net} の同じ path で、署名の query(ex・is・hm)を残す。
     * webp と gif にだけ {@code format=png} を付ける(webp は Java の ImageIO で読めない。gif は動く絵の代わりに
     * 1 枚で受け取る)。png・jpg はそのまま受け取る(写真を PNG に変えると何倍にも大きくなり、上限を超えるため)。
     */
    public URI fetchUri() {
        List<String> query = new ArrayList<>(params);
        if (convert) {
            query.add("format=png");
        }
        StringBuilder out = new StringBuilder("https://").append(FETCH_HOST).append(rawPath);
        if (!query.isEmpty()) {
            out.append('?').append(String.join("&", query));
        }
        return URI.create(out.toString());
    }

    /**
     * 添付の URL の期限({@code ex} の 16 進の UNIX 秒)を過ぎているか。{@code ex} が無い・読めないなら過ぎていない
     * と見る(取りにいって 404 なら失効と出す)。
     */
    public boolean expiredAt(long epochSeconds) {
        for (String param : params) {
            if (param.regionMatches(true, 0, "ex=", 0, 3)) {
                try {
                    return Long.parseLong(param.substring(3), 16) < epochSeconds;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }
}
