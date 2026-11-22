package gomacraft.utility.features.ttf_glyph_lock;

import gomacraft.utility.feature.Feature;

/**
 * TTF のフォントのグリフの読み込みを、FreeType の 1 つの lock で順に並べる(goma-org/gomabase#2295 の段 3「フォント」)。
 *
 * <p>Minecraft 1.21.1 の {@code TrueTypeGlyphProvider} は 1 つの {@code FT_Face}(グリフの置き場が 1 つ)を、
 * 文字の大きさを測る処理({@code getGlyph} —— {@code FT_Load_Glyph} の大きさだけの読み込み)と、焼き込む処理
 * ({@code NativeImage.copyFromFont} —— 描いて読み出す)で、同期の無いまま共有する。2 つが別のスレッドで重なると
 * (描画のスレッドの焼き込みと、別のスレッドの文字の幅の計算など)、焼き込みが別の字の絵を読み、
 * 「Glyph bitmap of size … does not match image of size …」で落ちる(2026-10-06 に Modern Font Pack で起きた形)。
 *
 * <p>直し方: その 2 つを、Minecraft 自身が FreeType の作成と後始末に使う lock({@code FreeTypeUtil.LIBRARY_LOCK})
 * で包む。中身は変えない(順に並べるだけ)。クライアントだけの機能。
 */
public final class TtfGlyphLock implements Feature {
    public static final String ID = "ttf_glyph_lock";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean clientOnly() {
        return true;
    }
}
