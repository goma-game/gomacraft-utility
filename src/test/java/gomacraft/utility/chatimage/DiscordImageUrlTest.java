package gomacraft.utility.chatimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 取りにいく先の境目(goma-game/gomacraft-utility#47): Discord の 2 つの置き場の添付の画像だけを通し、取りにいく先は
 * いつも media.discordapp.net になること。⚠ ここが緩むと、チャットの URL でほかの置き場へ通信させられる。
 */
class DiscordImageUrlTest {
    private static final String Q = "?ex=6750a1b2&is=674f5032&hm=abc123&";

    @Test
    void acceptsOnlyDiscordAttachmentImagesAndAlwaysFetchesFromTheMediaHost() {
        List<String> accepted = List.of(
                "https://cdn.discordapp.com/attachments/1/2/a.png" + Q,
                "https://media.discordapp.net/attachments/1/2/a.JPG",
                "https://CDN.discordapp.com:443/attachments/1/2/a.webp?format=webp&width=100",
                "https://cdn.discordapp.com/attachments/1/2/a.jpeg",
                "https://cdn.discordapp.com/attachments/1/2/a.gif");
        for (String url : accepted) {
            DiscordImageUrl parsed = DiscordImageUrl.parse(url).orElseThrow(() -> new AssertionError(url));
            assertEquals("https", parsed.fetchUri().getScheme(), url);
            assertEquals("media.discordapp.net", parsed.fetchUri().getHost(), url);
            assertEquals(-1, parsed.fetchUri().getPort(), url);
        }
        // 署名の query は残し、大きさ・形の指定は外す。PNG に変えてもらうのは webp と gif だけ
        assertEquals("https://media.discordapp.net/attachments/1/2/a.webp?format=png",
                DiscordImageUrl.parse(accepted.get(2)).orElseThrow().fetchUri().toString());
        assertEquals("https://media.discordapp.net/attachments/1/2/a.gif?format=png",
                DiscordImageUrl.parse(accepted.get(4)).orElseThrow().fetchUri().toString());
        assertEquals("https://media.discordapp.net/attachments/1/2/a.png?ex=6750a1b2&is=674f5032&hm=abc123",
                DiscordImageUrl.parse(accepted.get(0)).orElseThrow().fetchUri().toString());
        assertEquals("https://media.discordapp.net/attachments/1/2/a.JPG",
                DiscordImageUrl.parse(accepted.get(1)).orElseThrow().fetchUri().toString());

        List<String> refused = List.of(
                "http://cdn.discordapp.com/attachments/1/2/a.png",
                "https://cdn.discordapp.com.evil.example/attachments/1/2/a.png",
                "https://evil.example/cdn.discordapp.com/attachments/1/2/a.png",
                "https://evilcdn.discordapp.com/attachments/1/2/a.png",
                "https://discordapp.com/attachments/1/2/a.png",
                "https://user@cdn.discordapp.com/attachments/1/2/a.png",
                "https://cdn.discordapp.com@evil.example/attachments/1/2/a.png",
                "https://cdn.discordapp.com:8443/attachments/1/2/a.png",
                "https://cdn.discordapp.com/avatars/1/2/a.png",
                "https://cdn.discordapp.com/attachments/1/2/a.exe?x=.png",
                "https://cdn.discordapp.com/attachments/1/2/a.png/",
                "https://cdn.discordapp.com/attachments/1/2/a",
                "https://cdn.discordapp.com/attachments/../../evil/a.png",
                "https://cdn.discordapp.com/attachments/1/2/a.svg",
                "file:///attachments/1/2/a.png",
                "https:cdn.discordapp.com/attachments/1/2/a.png",
                "not a url",
                "");
        for (String url : refused) {
            assertFalse(DiscordImageUrl.parse(url).isPresent(), url);
        }
        assertFalse(DiscordImageUrl.parse(null).isPresent());
    }

    @Test
    void expiryComesFromTheExParameter() {
        DiscordImageUrl url = DiscordImageUrl.parse("https://cdn.discordapp.com/attachments/1/2/a.png?ex=10").orElseThrow();
        assertTrue(url.expiredAt(0x11));
        assertFalse(url.expiredAt(0x10));
        assertFalse(DiscordImageUrl.parse("https://cdn.discordapp.com/attachments/1/2/a.png").orElseThrow()
                .expiredAt(Long.MAX_VALUE));
    }
}
