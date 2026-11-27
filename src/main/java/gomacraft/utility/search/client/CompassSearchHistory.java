package gomacraft.utility.search.client;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.explorerscompass_search.ExplorersCompassScreenAccessor;
import gomacraft.utility.mixin.naturescompass_search.NaturesCompassScreenAccessor;
import gomacraft.utility.search.HistoryLayout;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.SearchHistorySettings;
import gomacraft.utility.settings.Settings;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * コンパス(Nature's / Explorer's)の検索語の履歴(goma-org/gomabase#2295 の mcdev の確かめの 8)。ウィンドウの形と操作は
 * {@link SearchHistoryOverlay}(AE2 の端末と共通)。帳はコンパスごとに別に持つ(アイテムの語・バイオームの語・構造物の語を
 * 混ぜない —— 5 回目の確かめで、2 つのコンパスの帳も分けた)。
 *
 * <p>欄はそれぞれのコンパスの検索の機能({@code naturescompass_search}・{@code explorerscompass_search})の accessor で
 * 引く。⚠ その機能の accessor が当たっているか({@code isLoaded} —— 起動のときに有効で、壊れていない)は描くたび・押すたびに
 * 見る(片方のコンパスの機能が後から FAILED になっても、もう片方のウィンドウは止めない —— #21 のレビュー)。設定の画面で
 * 英語名の検索を止めただけなら、履歴は動かす。画面が accessor を持つか({@code instanceof})で見分けるので、コンパスの class を名前で持たない。
 * 出す件数・持つ件数は AE2 の端末の履歴と同じ設定({@code SearchHistorySettings})。
 */
public final class CompassSearchHistory {
    /** 前の、2 つのコンパスで 1 つだった帳の名前(起動のときに 2 つへ分ける)。 */
    static final String OLD_SCOPE = "compass";
    /** コンパスごとの帳の名前(5 回目の確かめ —— バイオームの語と構造物の語を混ぜない)。 */
    static final String NATURES_SCOPE = "naturescompass";
    static final String EXPLORERS_SCOPE = "explorerscompass";

    /** 右上のボタン(テレポート。画面の右から 120 の所に幅 110)の手前。どちらのコンパスも同じ。 */
    private static final int RIGHT_BUTTON_FROM_EDGE = 120;

    private CompassSearchHistory() {
    }

    /**
     * 欄の右の空きの終わり(4 回目の確かめの 3 —— 欄の下に出すと 1 番目の結果を隠した)。⚠ どちらのコンパスも、右上に
     * テレポートのボタンを置く(出すのは許された人だけ)。出ていなくても同じ所で止める(ボタンの有無で形を変えない)。
     */
    static int compassSideRight(Screen screen) {
        return screen.width - RIGHT_BUTTON_FROM_EDGE - HistoryLayout.GAP;
    }

    public static void init(FeatureContext context) throws Exception {
        Settings.client().register(SearchHistorySettings.COLLAPSED_ROWS);
        Settings.client().register(SearchHistorySettings.VISIBLE_ROWS);
        Settings.client().register(SearchHistorySettings.KEEP);
        SearchHistoryOverlay.loadStore();
        // 前の 1 つの帳は、両方の帳へ写して分ける(今の記録を失わない。分けた後は写さない)
        SearchHistoryOverlay.splitScope(OLD_SCOPE, List.of(NATURES_SCOPE, EXPLORERS_SCOPE));
        SearchHistoryOverlay.Host natures = new SearchHistoryOverlay.Host() {
            @Override
            public EditBox field(Screen screen) {
                // accessor が当たっているかを問う(isLoaded)。⚠ isOn にしない: 英語名の検索を設定の画面で止めると、
                // 履歴まで黙って止まる(英語名の検索を止めても accessor は当たったまま)
                return Features.registry().isLoaded(SearchFeatures.NATURESCOMPASS_SEARCH)
                        && screen instanceof NaturesCompassScreenAccessor accessor
                        ? accessor.gomacraftUtility$searchField() : null;
            }

            @Override
            public void apply(Screen screen, EditBox field, String text) {
                field.setValue(text);
                ((NaturesCompassScreenAccessor) screen).gomacraftUtility$processSearchTerm();
            }

            @Override
            public HistoryLayout.Mode mode() {
                return HistoryLayout.Mode.RIGHT;
            }

            @Override
            public int sideRight(Screen screen) {
                return compassSideRight(screen);
            }
        };
        SearchHistoryOverlay.Host explorers = new SearchHistoryOverlay.Host() {
            @Override
            public EditBox field(Screen screen) {
                return Features.registry().isLoaded(SearchFeatures.EXPLORERSCOMPASS_SEARCH)
                        && screen instanceof ExplorersCompassScreenAccessor accessor
                        ? accessor.gomacraftUtility$searchField() : null;
            }

            @Override
            public void apply(Screen screen, EditBox field, String text) {
                field.setValue(text);
                ((ExplorersCompassScreenAccessor) screen).gomacraftUtility$processSearchTerm();
            }

            @Override
            public HistoryLayout.Mode mode() {
                return HistoryLayout.Mode.RIGHT;
            }

            @Override
            public int sideRight(Screen screen) {
                return compassSideRight(screen);
            }
        };
        // コンパスごとに別のウィンドウと帳(同じ機能 compass_search_history の中)
        new SearchHistoryOverlay(SearchFeatures.COMPASS_SEARCH_HISTORY, NATURES_SCOPE, List.of(natures)).register();
        new SearchHistoryOverlay(SearchFeatures.COMPASS_SEARCH_HISTORY, EXPLORERS_SCOPE, List.of(explorers)).register();
    }
}
