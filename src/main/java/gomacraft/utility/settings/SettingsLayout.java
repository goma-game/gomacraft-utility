package gomacraft.utility.settings;

import gomacraft.utility.emiae.EmiAeFeatures;
import gomacraft.utility.emiae.QuickCraftSettings;
import gomacraft.utility.features.ae2_repo_sort_cache.Ae2RepoSortCache;
import gomacraft.utility.features.ae2_repo_update_batch.Ae2RepoUpdateBatch;
import gomacraft.utility.features.ttf_glyph_lock.TtfGlyphLock;
import gomacraft.utility.screenshot.ScreenshotFeatures;
import gomacraft.utility.screenshot.ShareSettings;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.SearchHistorySettings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 設定の画面の並び(タブと、その中の木 —— goma-org/gomabase#2295 の 4 回目の確かめの 4)。Minecraft に依らない
 * (試験はここを見る)。
 *
 * <p>木の親子は「親が切なら子は効かない」の関係で決める(子は親の下に字下げして出し、親が切なら薄く出す)。
 * 設定の項目が属する機能({@link ClientSettings.Setting#feature} —— toml の節)とは別に持つ: 履歴の件数は
 * {@code [ae2_search_history]} の節に書くが、EMI とコンパスの履歴にも効く。
 *
 * <p>ここに置き忘れた機能・項目は、{@link #OTHER} のタブに並ぶ(画面から消えない)。
 */
public final class SettingsLayout {
    public static final String SEARCH = "search";
    public static final String AE2_EMI = "ae2_emi";
    public static final String SCREENSHOT = "screenshot";
    public static final String TEXT = "text";
    public static final String DEV = "dev";
    public static final String OTHER = "other";

    /** 1 行の幅・字下げ・右の操作の幅(画面と、名前の長さの試験が使う)。 */
    public static final int ROW_WIDTH = 320;
    public static final int INDENT = 12;
    public static final int CONTROL_WIDTH = 90;
    /** 名前と操作の間と、行の左の余白。 */
    public static final int GAP = 6;

    /** 1 つのタブ。 */
    public record Tab(String id, List<Node> nodes) {
        public String labelKey() {
            return "gomacraft_utility.settings.tab." + id;
        }
    }

    /** 木の 1 つ。 */
    public sealed interface Node permits FeatureNode, SettingNode, GroupNode {
        List<Node> children();

        String labelKey();

        /** ツールチップの訳の鍵(無ければ null)。 */
        String tooltipKey();
    }

    /** 機能の入り切り。 */
    public record FeatureNode(String featureId, List<Node> children) implements Node {
        @Override
        public String labelKey() {
            return "gomacraft_utility.feature." + featureId;
        }

        @Override
        public String tooltipKey() {
            return labelKey() + ".tooltip";
        }
    }

    /** 設定の項目。入り切りの項目なら、切のとき子は効かない。 */
    public record SettingNode(ClientSettings.Setting<?> setting, List<Node> children) implements Node {
        @Override
        public String labelKey() {
            return setting.translationKey();
        }

        @Override
        public String tooltipKey() {
            return labelKey() + ".tooltip";
        }
    }

    /**
     * 入り切りの無いまとまり(見出し)。{@code anyOf} が空でなければ、その機能のどれかが入のときだけ中身の項目が効く
     * (中の機能の入り切りそのものは、いつも触れる)。
     */
    public record GroupNode(String id, List<String> anyOf, List<Node> children) implements Node {
        @Override
        public String labelKey() {
            return "gomacraft_utility.settings.group." + id;
        }

        @Override
        public String tooltipKey() {
            return null;
        }
    }

    private SettingsLayout() {
    }

    private static FeatureNode feature(String id, Node... children) {
        return new FeatureNode(id, List.of(children));
    }

    private static SettingNode setting(ClientSettings.Setting<?> setting, Node... children) {
        return new SettingNode(setting, List.of(children));
    }

    private static GroupNode group(String id, List<String> anyOf, Node... children) {
        return new GroupNode(id, anyOf, List.of(children));
    }

    /** 並び(画面に出すかどうかで絞る前)。 */
    public static List<Tab> declared() {
        List<String> histories = List.of(SearchFeatures.AE2_SEARCH_HISTORY, SearchFeatures.EMI_SEARCH_HISTORY,
                SearchFeatures.COMPASS_SEARCH_HISTORY);
        return List.of(
                new Tab(SEARCH, List.of(
                        group("english_name", List.of(),
                                feature(SearchFeatures.AE2_ENGLISH_SEARCH),
                                feature(SearchFeatures.NATURESCOMPASS_SEARCH),
                                feature(SearchFeatures.EXPLORERSCOMPASS_SEARCH)),
                        group("history", List.of(),
                                feature(SearchFeatures.AE2_SEARCH_HISTORY),
                                feature(SearchFeatures.EMI_SEARCH_HISTORY),
                                feature(SearchFeatures.COMPASS_SEARCH_HISTORY),
                                group("history_rows", histories,
                                        setting(SearchHistorySettings.COLLAPSED_ROWS),
                                        setting(SearchHistorySettings.VISIBLE_ROWS),
                                        setting(SearchHistorySettings.KEEP))),
                        feature(SearchFeatures.AE2_SEARCH_KEY),
                        feature(SearchFeatures.AE2_SEARCH_SYNC_TOGGLE))),
                new Tab(AE2_EMI, List.of(
                        feature(EmiAeFeatures.STOCK,
                                feature(EmiAeFeatures.STOCK_QUERY)),
                        feature(EmiAeFeatures.CRAFT_CONFIRM),
                        feature(EmiAeFeatures.QUICK_CRAFT,
                                setting(QuickCraftSettings.ALT_CLICK,
                                        setting(QuickCraftSettings.SKIP_CONFIRM),
                                        feature(EmiAeFeatures.GRID_CRAFT))))),
                new Tab(SCREENSHOT, List.of(
                        feature(ScreenshotFeatures.SHARE,
                                setting(ShareSettings.DEFAULT_TARGET),
                                setting(ShareSettings.CHOOSER_MODIFIER)))),
                new Tab(TEXT, List.of(
                        feature(SearchFeatures.MCJTYLIB_TEXT))),
                // 遊ぶ人が触らなくてよいもの: 重さと不具合の直し(困ったときに切るためのもの)
                new Tab(DEV, List.of(
                        group("fixes", List.of(),
                                feature(SearchFeatures.AE2_TOOLTIP_SEARCH_CACHE),
                                feature(Ae2RepoSortCache.ID),
                                feature(Ae2RepoUpdateBatch.ID),
                                feature(TtfGlyphLock.ID)))));
    }

    /**
     * 画面に出す並び。出さない機能(一覧に無い・手元の設定に出さないもの)と宣言されていない項目を除き、空になった
     * まとまり・タブを除く。並びに無い機能・項目は {@link #OTHER} のタブの終わりに足す。{@link #DEV} のタブは、
     * 中身が無くても残す(起動時の状態を画面が足す)。
     */
    public static List<Tab> build(List<String> shownFeatures, List<ClientSettings.Setting<?>> settings) {
        Set<String> features = new HashSet<>(shownFeatures);
        Set<String> settingKeys = new HashSet<>();
        for (ClientSettings.Setting<?> s : settings) {
            settingKeys.add(key(s));
        }
        Set<String> placed = new HashSet<>();
        List<Tab> out = new ArrayList<>();
        for (Tab tab : declared()) {
            List<Node> nodes = filter(tab.nodes(), features, settingKeys, placed);
            if (!nodes.isEmpty() || tab.id().equals(DEV)) {
                out.add(new Tab(tab.id(), nodes));
            }
        }
        List<Node> rest = new ArrayList<>();
        for (String id : shownFeatures) {
            if (!placed.contains("f:" + id)) {
                rest.add(new FeatureNode(id, List.of()));
            }
        }
        for (ClientSettings.Setting<?> s : settings) {
            if (!placed.contains("s:" + key(s))) {
                rest.add(new SettingNode(s, List.of()));
            }
        }
        if (!rest.isEmpty()) {
            out.add(new Tab(OTHER, List.copyOf(rest)));
        }
        return List.copyOf(out);
    }

    private static String key(ClientSettings.Setting<?> s) {
        return s.feature() + "." + s.key();
    }

    private static List<Node> filter(List<Node> nodes, Set<String> features, Set<String> settings,
            Set<String> placed) {
        List<Node> out = new ArrayList<>();
        for (Node node : nodes) {
            List<Node> children = filter(node.children(), features, settings, placed);
            switch (node) {
                case FeatureNode f -> {
                    if (features.contains(f.featureId())) {
                        placed.add("f:" + f.featureId());
                        out.add(new FeatureNode(f.featureId(), children));
                    } else {
                        // 親の機能を出さないなら、子は親の段へ上げる(消さない)
                        out.addAll(children);
                    }
                }
                case SettingNode s -> {
                    if (settings.contains(key(s.setting()))) {
                        placed.add("s:" + key(s.setting()));
                        out.add(new SettingNode(s.setting(), children));
                    } else {
                        out.addAll(children);
                    }
                }
                case GroupNode g -> {
                    if (!children.isEmpty()) {
                        out.add(new GroupNode(g.id(), g.anyOf(), children));
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    /** 木の深さ(いちばん上は 0)ごとに数える。 */
    public static void walk(List<Node> nodes, int depth, java.util.function.ObjIntConsumer<Node> visit) {
        for (Node node : nodes) {
            visit.accept(node, depth);
            walk(node.children(), depth + 1, visit);
        }
    }
}
