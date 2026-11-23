package gomacraft.utility.search.client;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.explorerscompass_search.ExplorersCompassScreenAccessor;
import gomacraft.utility.mixin.naturescompass_search.NaturesCompassScreenAccessor;
import gomacraft.utility.search.SearchFeatures;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * コンパス(Nature's / Explorer's)の検索語の履歴(goma-org/gomabase#2295 の mcdev の確かめの 8)。ウィンドウの形と操作は
 * {@link SearchHistoryOverlay}(AE2 の端末と共通)。帳はコンパス用に別に持つ(アイテムの語とバイオーム・構造物の語を混ぜない)。
 *
 * <p>欄はそれぞれのコンパスの検索の機能({@code naturescompass_search}・{@code explorerscompass_search})の accessor で
 * 引く。⚠ その機能が有効かは描くたび・押すたびに見る(片方のコンパスの機能が後から FAILED になっても、もう片方のウィンドウは止めない
 * —— #21 のレビュー)。画面が accessor を持つか({@code instanceof})で見分けるので、コンパスの class を名前で持たない。
 * 出す件数・持つ件数は AE2 の端末の履歴と同じ設定({@code SearchHistorySettings})。
 */
public final class CompassSearchHistory {
    /** コンパスの帳の名前(履歴のファイルの中で、プレイヤーの帳と分ける)。 */
    static final String SCOPE = "compass";

    private CompassSearchHistory() {
    }

    public static void init(FeatureContext context) throws Exception {
        SearchHistoryOverlay.loadStore();
        SearchHistoryOverlay.Host natures = new SearchHistoryOverlay.Host() {
            @Override
            public EditBox field(Screen screen) {
                return Features.registry().isOn(SearchFeatures.NATURESCOMPASS_SEARCH)
                        && screen instanceof NaturesCompassScreenAccessor accessor
                        ? accessor.gomacraftUtility$searchField() : null;
            }

            @Override
            public void apply(Screen screen, EditBox field, String text) {
                field.setValue(text);
                ((NaturesCompassScreenAccessor) screen).gomacraftUtility$processSearchTerm();
            }
        };
        SearchHistoryOverlay.Host explorers = new SearchHistoryOverlay.Host() {
            @Override
            public EditBox field(Screen screen) {
                return Features.registry().isOn(SearchFeatures.EXPLORERSCOMPASS_SEARCH)
                        && screen instanceof ExplorersCompassScreenAccessor accessor
                        ? accessor.gomacraftUtility$searchField() : null;
            }

            @Override
            public void apply(Screen screen, EditBox field, String text) {
                field.setValue(text);
                ((ExplorersCompassScreenAccessor) screen).gomacraftUtility$processSearchTerm();
            }
        };
        new SearchHistoryOverlay(SearchFeatures.COMPASS_SEARCH_HISTORY, SCOPE, List.of(natures, explorers)).register();
    }
}
