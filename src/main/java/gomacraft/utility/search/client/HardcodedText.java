package gomacraft.utility.search.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.locale.Language;

/**
 * 英語で直に書かれた文言(訳のキーを持たない文字列)に、訳を当てる差し込み口。訳の中身は Mod の外(訳のパック)が持つ。
 *
 * <p>約束: 訳のパックの言語のファイル(どの名前空間の {@code lang/<言語>.json} でもよい)に、キー
 * {@code gomacraft_utility.literal.<英文そのまま>} で訳を置く。英文は画面に出る文字列と完全に一致させる
 * (前後の空白・大文字小文字も)。キーが無ければ英文のまま出す。複数行のツールチップは、行を改行でつないだ英文の
 * キーを先に引き(訳も改行で行に分ける)、無ければ行ごとに引く({@link #translateAll})。
 */
public final class HardcodedText {
    public static final String KEY_PREFIX = "gomacraft_utility.literal.";

    private static final Map<String, String> CACHE = new HashMap<>();
    private static Language cachedFor;

    private HardcodedText() {
    }

    /** 描画の thread からだけ呼ぶ。 */
    public static synchronized String translate(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Language language = Language.getInstance();
        if (language != cachedFor) {
            CACHE.clear();
            cachedFor = language;
        }
        String cached = CACHE.get(text);
        if (cached == null) {
            String key = KEY_PREFIX + text;
            cached = language.has(key) ? language.getOrDefault(key) : text;
            if (CACHE.size() > 4096) {
                CACHE.clear();
            }
            CACHE.put(text, cached);
        }
        return cached;
    }

    /**
     * 複数行(ツールチップ)に訳を当てた写し(元の一覧は変えない)。1 文を行に分けて書いたものがあるので、まず行を改行で
     * つないだまとまりのキーを引き、あればその訳を改行で分けて返す。無ければ行ごとに {@link #translate} を当てる。
     */
    public static List<String> translateAll(List<String> lines) {
        return gomacraft.utility.search.LiteralLines.translate(lines, HardcodedText::translate);
    }
}
