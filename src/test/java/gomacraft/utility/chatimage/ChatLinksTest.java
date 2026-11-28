package gomacraft.utility.chatimage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * チャットの URL の切り方(#50 のレビュー): 日本語の文・全角の空白・句読点を URL に入れない。日本語の path は残す。
 */
class ChatLinksTest {
    private static List<String> urls(String text) {
        return ChatLinks.find(text).stream().map(s -> text.substring(s.start(), s.end())).toList();
    }

    @Test
    void cutsAtJapaneseTextAndPunctuationButKeepsJapanesePaths() {
        assertEquals(List.of("https://x.example/a.png"), urls("https://x.example/a.pngこれ見て"));
        assertEquals(List.of("https://x.example/a"), urls("https://x.example/a。次"));
        assertEquals(List.of("https://x.example/a", "https://x.example/b"),
                urls("https://x.example/a、https://x.example/b"));
        assertEquals(List.of("https://x.example/a"), urls("https://x.example/a　次"));
        assertEquals(List.of("https://x.example/a"), urls("「https://x.example/a」"));
        assertEquals(List.of("https://ja.wikipedia.org/wiki/日本"), urls("見て https://ja.wikipedia.org/wiki/日本 です"));
        assertEquals(List.of("https://x.example/search?q=日本"), urls("https://x.example/search?q=日本"));
        assertEquals(List.of("https://en.wikipedia.org/wiki/Foo_(bar)"), urls("https://en.wikipedia.org/wiki/Foo_(bar)."));
        assertEquals(List.of("https://x.example/a"), urls("(https://x.example/a)"));
        assertEquals(List.of(), urls("https:// と http://"));
        assertEquals(List.of(), urls("example.com"));
    }
}
