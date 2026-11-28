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
    private static final String FETCH_HOST = "media.discordapp.net";
    /** 組み直すときに外す query(こちらで付け直す・大きさを変えさせない)。 */
    private static final Set<String> DROPPED_PARAMS = Set.of("format", "width", "height", "quality");

    private final String original;
    private final String rawPath;
    private final List<String> params;

    private DiscordImageUrl(String original, String rawPath, List<String> params) {
        this.original = original;
        this.rawPath = rawPath;
        this.params = params;
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
        if (rawPath == null || !rawPath.startsWith("/attachments/") || rawPath.contains("/../")
                || rawPath.contains("\\")) {
            return Optional.empty();
        }
        String path = uri.getPath();
        int dot = path.lastIndexOf('.');
        if (dot < 0 || path.lastIndexOf('/') > dot) {
            return Optional.empty();
        }
        if (!EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT))) {
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
        return Optional.of(new DiscordImageUrl(url, rawPath, List.copyOf(params)));
    }

    /** チャットに書かれていた元の URL(ブラウザで開くとき)。 */
    public String original() {
        return original;
    }

    /**
     * 取りにいく先。いつも {@code https://media.discordapp.net} の同じ path で、署名の query(ex・is・hm)を残し、
     * {@code format=png} を付ける(webp なども PNG で受け取る)。
     */
    public URI fetchUri() {
        StringBuilder out = new StringBuilder("https://").append(FETCH_HOST).append(rawPath).append('?');
        for (String param : params) {
            out.append(param).append('&');
        }
        out.append("format=png");
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
