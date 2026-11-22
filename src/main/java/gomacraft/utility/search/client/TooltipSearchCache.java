package gomacraft.utility.search.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.search.RepoSearch;
import appeng.menu.me.common.GridInventoryEntry;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.ae2_tooltip_search_cache.MEStorageScreenRepoAccessor;
import gomacraft.utility.search.SearchFeatures;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * AE2 の端末のツールチップの検索({@code $})の作り置き(品 → ツールチップの文)を、端末をまたいで持ち、端末を開いている
 * 間に少しずつ先に作る(mcdev の結果 7 —— 最初の検索で全部の品のツールチップを 1 度に作るので、待たされてカクついた)。
 *
 * <ul>
 *   <li>作り置きの入れ物は AE2 の検索が使うものを差し替える({@code RepoSearchMixin})。中身(ツールチップの文の作り方)は
 *       AE2 のまま: 先に作るときも、AE2 の検索(印 {@code $} だけの検索語)に品を 1 つずつ問うて作らせる。</li>
 *   <li>先に作るのは、端末を開いている間の tick ごとに {@link #BUDGET_NANOS} まで(描画を止めない)。</li>
 *   <li>表示の言語が変われば(ツールチップの文が変わる)、作り置きを捨てる。</li>
 * </ul>
 */
public final class TooltipSearchCache {
    /** 1 tick に先に作る時間の上限(2 ms)。 */
    private static final long BUDGET_NANOS = 2_000_000L;

    private static final Map<AEKey, String> SHARED = new WeakHashMap<>();
    private static Language cachedFor;
    /** 先に作るための検索(印 {@code $} だけ = 全部の品に当たる)。問うた品のツールチップの文を {@link #SHARED} に作る。 */
    private static RepoSearch warmer;

    private TooltipSearchCache() {
    }

    public static void init(FeatureContext context) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> Features.guard(SearchFeatures.AE2_TOOLTIP_SEARCH_CACHE, TooltipSearchCache::warm));
    }

    /** AE2 の検索が使う、端末をまたぐ作り置き(描画の thread からだけ使う)。 */
    public static Map<AEKey, String> shared() {
        Language language = Language.getInstance();
        if (language != cachedFor) {
            SHARED.clear();
            cachedFor = language;
            warmer = null;
        }
        return SHARED;
    }

    private static void warm() {
        if (!(Minecraft.getInstance().screen instanceof MEStorageScreen<?> screen)) {
            return;
        }
        Map<AEKey, String> cache = shared();
        if (warmer == null) {
            warmer = new RepoSearch();
            // 印 $ だけの検索語: AE2 のツールチップの検索を、空の語で作る(作るときに上の作り置きを使う —— mixin)
            warmer.setSearchString("$");
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        for (GridInventoryEntry entry : ((MEStorageScreenRepoAccessor) screen).gomacraftUtility$tooltipRepo().getAllEntries()) {
            AEKey what = entry.getWhat();
            if (what == null || cache.containsKey(what)) {
                continue;
            }
            warmer.matches(entry);
            if (System.nanoTime() > deadline) {
                return;
            }
        }
    }
}
