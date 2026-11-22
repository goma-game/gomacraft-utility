package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.ae2_search_history.MEStorageScreenHistoryAccessor;
import gomacraft.utility.search.SearchFeatures;
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
 *   <li>出す件数(空のとき 3・広げたとき 10)と持つ件数(100)は今は定数で、設定の画面(ClientSettings)が入った後にそこへ載せる。</li>
 *   <li>記すのは、窓の決まり(Enter・欄の外・閉じたとき)に加えて、F キーで送ったときと、EMI の検索欄から focus が
 *       外れたとき(AE2 と EMI の履歴を 1 つにする)。</li>
 *   <li>帳はプレイヤーの帳そのまま(前からの履歴を引き継ぐ)。</li>
 * </ul>
 */
public final class Ae2SearchHistory {
    /** 欄が空でホバーしていないときに出す件数。 */
    static final int COLLAPSED_ROWS = 3;
    /** 広げたとき(ホバー・入力中)に一度に出す件数。それより古いものはホイールで遡る。 */
    static final int VISIBLE_ROWS = 10;
    /** 持つ件数(お気に入りは数えない。古いものから消す)。 */
    static final int KEEP = 100;

    private static SearchHistoryOverlay overlay;
    /** 前の tick に EMI の検索欄に focus があったか。 */
    private static boolean emiFocused;

    private Ae2SearchHistory() {
    }

    public static void init(FeatureContext context) throws Exception {
        // 件数は今は既定の値の定数。設定の画面(ClientSettings —— #12)が入った後に、そこへ載せる
        int collapsedRows = COLLAPSED_ROWS;
        int visibleRows = Math.max(collapsedRows, VISIBLE_ROWS);
        int keep = KEEP;
        SearchHistoryOverlay.loadStore(keep);
        overlay = new SearchHistoryOverlay(SearchFeatures.AE2_SEARCH_HISTORY, "", List.of(new SearchHistoryOverlay.Host() {
            @Override
            public EditBox field(Screen screen) {
                return Ae2SearchHistory.field(screen);
            }

            @Override
            public void apply(Screen screen, EditBox field, String text) {
                Ae2Search.apply(field, text);
            }
        }), collapsedRows, visibleRows);
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
