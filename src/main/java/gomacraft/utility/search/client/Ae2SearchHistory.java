package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.ae2_search_history.MEStorageScreenHistoryAccessor;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.settings.ClientSettings;
import gomacraft.utility.settings.Settings;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * AE2 の端末の検索語の履歴(goma-org/gomabase#2295)。窓の形と操作は {@link SearchHistoryOverlay}(コンパスと共通)。
 *
 * <ul>
 *   <li>出す件数(空のとき・広げたとき)と持つ件数は設定の画面({@code ClientSettings}。既定は 3・10・100)で変えられ、
 *       すぐ効く(持つ件数は次に記すときから)。コンパスの履歴も同じ値を使う。</li>
 *   <li>記すのは、窓の決まり(Enter・欄の外・閉じたとき)に加えて、F キーで送ったときと、EMI の検索欄から focus が
 *       外れたとき(AE2 と EMI の履歴を 1 つにする)。</li>
 *   <li>帳はプレイヤーの帳そのまま(前からの履歴を引き継ぐ)。</li>
 * </ul>
 */
public final class Ae2SearchHistory {
    /** 欄が空でホバーしていないときに出す件数(設定の画面で変えられる。すぐ効く)。 */
    public static final ClientSettings.Int COLLAPSED_ROWS =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "collapsed_rows", 3, 1, 20);
    /** 広げたとき(ホバー・入力中)に一度に出す件数。それより古いものはホイールで遡る。 */
    public static final ClientSettings.Int VISIBLE_ROWS =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "visible_rows", 10, 1, 30);
    /** 持つ件数(お気に入りは数えない。古いものから消す)。 */
    public static final ClientSettings.Int KEEP =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "keep", 100, 10, 1000);

    private static SearchHistoryOverlay overlay;
    /** 前の tick に EMI の検索欄に focus があったか。 */
    private static boolean emiFocused;

    private Ae2SearchHistory() {
    }

    public static void init(FeatureContext context) throws Exception {
        Settings.client().register(COLLAPSED_ROWS);
        Settings.client().register(VISIBLE_ROWS);
        Settings.client().register(KEEP);
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

    /** EMI の検索欄から focus が外れたら、その語を記す(どの画面でも)。 */
    private static void watchEmi() {
        boolean focused = EmiBridge.isSearchFocused();
        if (emiFocused && !focused && overlay != null) {
            overlay.record(EmiBridge.getSearchText());
        }
        emiFocused = focused;
    }
}
