package gomacraft.utility.features.ae2_repo_sort_cache;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.Requirement;
import java.util.List;

/**
 * AE2 の端末の一覧の並べ直しを軽くする(goma-org/gomabase#2295 の段 3「重さ」)。
 *
 * <p>端末を開いた直後・ユニバーサルターミナルを切り替えた直後は、ネットワークの全在庫が在庫の差分の通信
 * ({@code MEInventoryUpdatePacket})何本かに分かれて届く。AE2 の {@code Repo} は 1 本ごとに一覧の全部を並べ直し、
 * 並べ直しの比べのたびに Mod の id と表示名の文字列を作り直す(比べる回数 × 文字列の組み立て)。ユーザの spark では、
 * 開く・切り替えるときの描画のスレッドの待ちの 65% がこの並べ直しだった。
 *
 * <p>直し方(AE2 の並べ方 —— 順序 —— は変えない):
 * <ol>
 *   <li>並べ替えの鍵(Mod の id・表示名)を、1 回の並べ直しの中で品ごとに 1 回だけ作る({@link SortKeys})。</li>
 *   <li>差分の通信が届いても、その場では並べ直さない。一覧を読まれる前(描画が一覧を読むとき)に 1 回だけ並べ直す。
 *       同じフレームに何本届いても 1 回で済む。手を止めている間(Shift を押して一覧を止めている間)は、AE2 の
 *       足し方(並べ直さずに後ろへ足す)をそのまま通す。</li>
 * </ol>
 *
 * <p>クライアントだけの機能。
 */
public final class Ae2RepoSortCache implements Feature {
    public static final String ID = "ae2_repo_sort_cache";

    /** AE2 の版の範囲。当て先が変わったときは、版ではなく mixin の当て損ね(FAILED)で気付く。 */
    static final String AE2_RANGE = "[19.2,20)";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Requirement> requirements() {
        return List.of(Requirement.of("ae2", AE2_RANGE));
    }

    @Override
    public boolean clientOnly() {
        return true;
    }
}
