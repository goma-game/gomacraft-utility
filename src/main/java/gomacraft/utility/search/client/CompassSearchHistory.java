package gomacraft.utility.search.client;

import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.naturescompass.gui.NaturesCompassScreen;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.explorerscompass_search.ExplorersCompassScreenAccessor;
import gomacraft.utility.mixin.naturescompass_search.NaturesCompassScreenAccessor;
import gomacraft.utility.search.SearchFeatures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * コンパス(Nature's / Explorer's)の検索語の履歴(goma-org/gomabase#2295 の mcdev の確かめの 8)。窓の形と操作は
 * {@link SearchHistoryOverlay}(AE2 の端末と共通)。帳はコンパス用に別に持つ(品の語とバイオーム・構造物の語を混ぜない)。
 *
 * <p>欄はそれぞれのコンパスの検索の機能({@code naturescompass_search}・{@code explorerscompass_search})の accessor で
 * 引くので、その機能が有効なコンパスだけに窓を出す。出す件数・持つ件数は AE2 の端末の履歴の設定と同じ値を使う。
 */
public final class CompassSearchHistory {
    /** コンパスの帳の名前(履歴のファイルの中で、プレイヤーの帳と分ける)。 */
    static final String SCOPE = "compass";

    private CompassSearchHistory() {
    }

    public static void init(FeatureContext context) throws Exception {
        SearchHistoryOverlay.loadStore();
        List<SearchHistoryOverlay.Host> hosts = new ArrayList<>();
        if (Features.registry().isOn(SearchFeatures.NATURESCOMPASS_SEARCH)) {
            hosts.add(new SearchHistoryOverlay.Host() {
                @Override
                public EditBox field(Screen screen) {
                    return screen instanceof NaturesCompassScreen
                            ? ((NaturesCompassScreenAccessor) screen).gomacraftUtility$searchField() : null;
                }

                @Override
                public void apply(Screen screen, EditBox field, String text) {
                    field.setValue(text);
                    ((NaturesCompassScreen) screen).processSearchTerm();
                }
            });
        }
        if (Features.registry().isOn(SearchFeatures.EXPLORERSCOMPASS_SEARCH)) {
            hosts.add(new SearchHistoryOverlay.Host() {
                @Override
                public EditBox field(Screen screen) {
                    return screen instanceof ExplorersCompassScreen
                            ? ((ExplorersCompassScreenAccessor) screen).gomacraftUtility$searchField() : null;
                }

                @Override
                public void apply(Screen screen, EditBox field, String text) {
                    field.setValue(text);
                    ((ExplorersCompassScreen) screen).processSearchTerm();
                }
            });
        }
        if (hosts.isEmpty()) {
            return;
        }
        new SearchHistoryOverlay(SearchFeatures.COMPASS_SEARCH_HISTORY, SCOPE, hosts).register();
    }
}
