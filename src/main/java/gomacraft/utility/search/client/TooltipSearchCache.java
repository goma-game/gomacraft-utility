package gomacraft.utility.search.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.me.search.RepoSearch;
import appeng.menu.me.common.GridInventoryEntry;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.ae2_tooltip_search_cache.MEStorageScreenRepoAccessor;
import gomacraft.utility.search.SearchFeatures;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * AE2 の端末のツールチップの検索({@code $})のキャッシュ(アイテム → ツールチップの文)を、端末をまたいで持ち、端末を開いている
 * 間に少しずつ先に作る(mcdev の結果 7 —— 最初の検索で全部のアイテムのツールチップを 1 度に作るので、待たされてカクついた)。
 *
 * <ul>
 *   <li>作り置きの入れ物は AE2 の検索が使うものを差し替える({@code RepoSearchMixin})。中身(ツールチップの文の作り方)は
 *       AE2 のまま: 先に作るときも、AE2 の検索(印 {@code $} だけの検索語)にアイテムを 1 つずつ問うて作らせる。</li>
 *   <li>入れ物は強い参照で持つ(AE2 のアイテムの鍵は届くたびに作り直されるので、弱い参照だと端末を閉じた後に消える)。
 *       件数は {@link #MAX_ENTRIES} まで(古いものから捨てる)。サーバから抜けたら捨てる。表示の言語が変わったら捨てる。</li>
 *   <li>先に作るのは、端末を開いている間の tick ごとに {@link #BUDGET_NANOS} まで(描画を止めない)。走査の位置を持ち、
 *       作り終えた端末では回らない。アイテムの数が変わったら、頭から見直す(作ったアイテムは飛ばす)。</li>
 * </ul>
 */
public final class TooltipSearchCache {
    /** 1 tick に先に作る時間の上限(2 ms。1 つ作り始める前に見る)。 */
    private static final long BUDGET_NANOS = 2_000_000L;
    /** 作り置きの件数の上限(古いものから捨てる)。 */
    static final int MAX_ENTRIES = 50_000;

    private static final Map<AEKey, String> SHARED = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<AEKey, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private static Language cachedFor;

    /** 先に作っている端末のアイテムの一覧(変われば作り直す)。 */
    private static Repo warmRepo;
    /** 先に作るための検索(印 {@code $} だけ)。AE2 の検索はアイテムごとの答えをアイテムの番号で覚え、番号は端末ごとに振り直されるので、端末ごとに作る。 */
    private static RepoSearch warmer;
    /** 先に作るアイテムの並びと、次に見る位置。 */
    private static List<GridInventoryEntry> pending = List.of();
    private static int cursor;
    private static int seenSize = -1;

    private TooltipSearchCache() {
    }

    public static void init(FeatureContext context) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> Features.guard(SearchFeatures.AE2_TOOLTIP_SEARCH_CACHE, TooltipSearchCache::warm));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                event -> Features.guard(SearchFeatures.AE2_TOOLTIP_SEARCH_CACHE, TooltipSearchCache::reset));
    }

    /** AE2 の検索が使う、端末をまたぐ作り置き(描画の thread からだけ使う)。 */
    public static Map<AEKey, String> shared() {
        Language language = Language.getInstance();
        if (language != cachedFor) {
            reset();
            cachedFor = language;
        }
        return SHARED;
    }

    private static void reset() {
        SHARED.clear();
        warmRepo = null;
        warmer = null;
        pending = List.of();
        cursor = 0;
        seenSize = -1;
    }

    private static void warm() {
        if (!(Minecraft.getInstance().screen instanceof MEStorageScreen<?> screen)) {
            return;
        }
        Map<AEKey, String> cache = shared();
        Repo repo = ((MEStorageScreenRepoAccessor) screen).gomacraftUtility$tooltipRepo();
        var all = repo.getAllEntries();
        if (repo != warmRepo) {
            warmRepo = repo;
            warmer = new RepoSearch();
            // 印 $ だけの検索語: AE2 のツールチップの検索を、空の語で作る(作るときに上の作り置きを使う —— mixin)
            warmer.setSearchString("$");
            seenSize = -1;
        }
        if (all.size() != seenSize) {
            // アイテムが増減した(端末を開いた直後・在庫の変化)。頭から見直す(作ったアイテムは飛ばすので安い)
            seenSize = all.size();
            pending = new ArrayList<>(all);
            cursor = 0;
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (cursor < pending.size() && System.nanoTime() < deadline) {
            GridInventoryEntry entry = pending.get(cursor++);
            AEKey what = entry.getWhat();
            if (what != null && !cache.containsKey(what)) {
                warmer.matches(entry);
            }
        }
    }
}
