package gomacraft.utility.search.client;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.explorerscompass_search.ExplorersCompassScreenAccessor;
import gomacraft.utility.mixin.naturescompass_search.NaturesCompassScreenAccessor;
import gomacraft.utility.search.HistoryLayout;
import gomacraft.utility.search.SearchFeatures;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * コンパス(Nature's / Explorer's)の検索語の履歴(goma-org/gomabase#2295 の mcdev の確かめの 8)。ウィンドウの形と操作は
 * {@link SearchHistoryOverlay}(AE2 の端末と共通)。帳はコンパス用に別に持つ(アイテムの語とバイオーム・構造物の語を混ぜない)。
 *
 * <p>欄はそれぞれのコンパスの検索の機能({@code naturescompass_search}・{@code explorerscompass_search})の accessor で
 * 引く。⚠ その機能の accessor が当たっているか({@code isLoaded} —— 起動のときに有効で、壊れていない)は描くたび・押すたびに
 * 見る(片方のコンパスの機能が後から FAILED になっても、もう片方のウィンドウは止めない —— #21 のレビュー)。設定の画面で
 * 英語名の検索を止めただけなら、履歴は動かす。画面が accessor を持つか({@code instanceof})で見分けるので、コンパスの class を名前で持たない。
 * 出す件数・持つ件数は AE2 の端末の履歴と同じ設定({@code SearchHistorySettings})。
 */
public final class CompassSearchHistory {
    /** コンパスの帳の名前(履歴のファイルの中で、プレイヤーの帳と分ける)。 */
    static final String SCOPE = "compass";

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
        SearchHistoryOverlay.loadStore();
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
        new SearchHistoryOverlay(SearchFeatures.COMPASS_SEARCH_HISTORY, SCOPE, List.of(natures, explorers)).register();
    }
}
