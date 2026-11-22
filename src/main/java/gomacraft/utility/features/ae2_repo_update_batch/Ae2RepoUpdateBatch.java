package gomacraft.utility.features.ae2_repo_update_batch;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.Requirement;
import java.util.List;

/**
 * AE2 の端末の一覧の並べ直しを、一覧を読まれる前の 1 回にまとめる(goma-org/gomabase#2295 の段 3「重さ」)。
 *
 * <p>端末を開いた直後・ユニバーサルターミナルを切り替えた直後は、ネットワークの全在庫が在庫の差分の通信
 * ({@code MEInventoryUpdatePacket})何本かに分かれて届く。AE2 の {@code Repo} は 1 本ごとに一覧の全部を並べ直す。
 * ユーザの spark では、開く・切り替えるときの描画のスレッドの待ちの 65% が並べ直しだった。
 *
 * <p>直し方: 差分の通信が届いても、その場では並べ直さない。一覧を読まれる前(描画が一覧を読むとき)に 1 回だけ
 * 並べ直す。同じフレームに何本届いても 1 回で済む。手を止めている間(Shift を押して一覧を止めている間)は、AE2 の
 * 足し方(並べ直さずに後ろへ足す)をそのまま通す。並べ方(順序)は変えない。
 *
 * <p>⚠ 並べ直す時点を遅らせるので、困ったときにこれだけを切れるよう、鍵の作り置き({@code ae2_repo_sort_cache})と
 * 機能を分けた。⚠ 一覧({@code Repo} の {@code view})を直に読む Mod は、読む口を通らないと古い一覧を見る
 * (ATM10 の Bulk Terminal はリフレクションで読むが、読むのは {@code updateView} の終わりの知らせの中だけなので、
 * 並べ直した後の一覧を見る)。
 *
 * <p>クライアントだけの機能。
 */
public final class Ae2RepoUpdateBatch implements Feature {
    public static final String ID = "ae2_repo_update_batch";

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
