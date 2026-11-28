package gomacraft.utility.features.ae2_repo_sort_cache;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.Requirement;
import java.util.List;

/**
 * AE2 の端末の一覧の並べ替えの鍵を作り置いて、並べ直しを軽くする(goma-org/gomabase#2295 の段 3「重さ」)。
 *
 * <p>AE2 の {@code Repo} は、並べ直しの比べ({@code KeySorters})のたびに、Mod の id と表示名の文字列を作り直す
 * (比べる回数 × 文字列の組み立て)。ユーザの spark では、端末を開く・ユニバーサルターミナルを切り替えるときの
 * 描画のスレッドの待ちの 65% が並べ直しで、その中の多くがこの作り直しだった。
 *
 * <p>直し方: 鍵(Mod の id と表示名)を品ごとに作り置き、並べ直しをまたいで持ち越す({@code client.Ae2SortKeys})。
 * 比べ方の形は {@link SortKeys}。並べ方(順序)は変えない。作り置きは次のときに捨てる: 言語が変わったとき(表示名が
 * 変わる)・ログアウトしたとき・設定の画面でこの機能を止めた・動かし直したとき・件数が上限(5 万)に届いたとき
 * (並べ直しの入口で見る)。
 * 届いた通信の並べ直しを 1 回にまとめるのは、別の機能({@code ae2_repo_update_batch})。
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
    public void init(FeatureContext context) {
        gomacraft.utility.features.ae2_repo_sort_cache.client.Ae2SortKeys.init();
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
