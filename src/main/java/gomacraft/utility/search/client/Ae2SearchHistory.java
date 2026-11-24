package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.widgets.AETextField;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.ae2_search_history.MEStorageScreenHistoryAccessor;
import gomacraft.utility.search.HistoryLayout;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.SearchHistorySettings;
import gomacraft.utility.settings.Settings;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * AE2 の端末の検索語の履歴(goma-org/gomabase#2295)。ウィンドウの形と操作は {@link SearchHistoryOverlay}(コンパスと共通)。
 *
 * <ul>
 *   <li>出す件数(空のとき・広げたとき)と持つ件数は設定の画面({@code ClientSettings}。既定は 3・10・100)で変えられ、
 *       すぐ効く(持つ件数は次に記すときから)。コンパスの履歴も同じ値を使う。</li>
 *   <li>記すのは、ウィンドウの決まり(focus が外れたとき・Enter・欄の外を押したとき・閉じたとき —— {@code FocusRecorder})
 *       に加えて、F キーで送ったとき。</li>
 *   <li>EMI の検索欄の語も同じ帳に記す(AE2 と EMI の履歴を 1 つにする)。記すのは EMI の欄の履歴の機能
 *       ({@code emi_search_history})のウィンドウの決まり。その機能が切れているときだけ、ここが EMI の欄から focus が
 *       外れたのを見て記す({@link #watchEmi})。</li>
 *   <li>帳はプレイヤーの帳そのまま(前からの履歴を引き継ぐ)。</li>
 * </ul>
 */
public final class Ae2SearchHistory {
    private static SearchHistoryOverlay overlay;
    /** 前の tick に EMI の検索欄に focus があったか。 */
    private static boolean emiFocused;

    private Ae2SearchHistory() {
    }

    public static void init(FeatureContext context) throws Exception {
        Settings.client().register(SearchHistorySettings.COLLAPSED_ROWS);
        Settings.client().register(SearchHistorySettings.VISIBLE_ROWS);
        Settings.client().register(SearchHistorySettings.KEEP);
        SearchHistoryOverlay.loadStore();
        overlay = new SearchHistoryOverlay(SearchFeatures.AE2_SEARCH_HISTORY, "", List.of(new SearchHistoryOverlay.Host() {
            @Override
            public EditBox field(Screen screen) {
                return Ae2SearchHistory.field(screen);
            }

            @Override
            public void apply(Screen screen, EditBox field, String text) {
                Ae2Search.apply(field, text);
            }

            @Override
            public HistoryLayout.Mode mode() {
                return HistoryLayout.Mode.TERMINAL;
            }

            @Override
            public HistoryLayout.Rect bounds(EditBox field) {
                // AE2 の欄の見た目の枠(字の位置より 2 ずつ外。AETextField#getTooltipArea)
                Rect2i area = ((AETextField) field).getTooltipArea();
                return new HistoryLayout.Rect(area.getX(), area.getY(), area.getWidth(), area.getHeight());
            }
        }));
        overlay.register();
        if (ModList.get().isLoaded("emi")) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> guard(Ae2SearchHistory::watchEmi));
        }
    }

    private static void guard(Runnable body) {
        Features.guard(SearchFeatures.AE2_SEARCH_HISTORY, body);
    }

    /** ほかの機能(F キー・EMI から写すキー)が検索語を入れたときに記す。履歴の機能が切れていれば何もしない。 */
    static void recordFromOtherFeature(String text) {
        guard(() -> {
            if (overlay != null) {
                overlay.record(text);
            }
        });
    }

    private static EditBox field(Screen screen) {
        if (!(screen instanceof MEStorageScreen<?>)) {
            return null;
        }
        return ((MEStorageScreenHistoryAccessor) screen).gomacraftUtility$historySearchField();
    }

    /** EMI の欄の履歴の機能が切れているとき、EMI の検索欄から focus が外れたら、その語を記す(どの画面でも)。 */
    private static void watchEmi() {
        // EMI の検索欄の履歴の機能が有効なら、そちらのウィンドウが focus の見張りで記すので、ここは何もしない
        // (同じ帳へ 2 度記さない)。切れているときだけ、ここが EMI の欄の語を記す
        if (Features.registry().isOn(SearchFeatures.EMI_SEARCH_HISTORY)) {
            emiFocused = false;
            return;
        }
        boolean focused = EmiBridge.isSearchFocused();
        if (emiFocused && !focused && overlay != null) {
            overlay.record(EmiBridge.getSearchText());
        }
        emiFocused = focused;
    }
}
