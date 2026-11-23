package gomacraft.utility.search.client;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.search.HistoryLayout;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.SearchHistorySettings;
import gomacraft.utility.settings.Settings;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * EMI の検索欄の検索語の履歴(goma-org/gomabase#2295 の 2 回目の確かめの 2)。ウィンドウの形と操作は
 * {@link SearchHistoryOverlay}、置き方は欄の左({@link HistoryLayout.Mode#EMI})。帳は AE2 の端末と同じ(EMI の語も
 * AE2 の語も 1 つの履歴)。出す件数は AE2 の端末の履歴と同じ設定。
 *
 * <ul>
 *   <li>⚠ EMI は、押す・キーを画面の出来事(ScreenEvent)より先に自分で受ける(EMI の MouseMixin・KeyboardMixin)。
 *       欄の外を押せば欄の focus を外し、↑↓は EMI 自身の履歴に使う。だから押す・キーは EMI の入口
 *       ({@code EmiScreenManager#mouseClicked}・{@code #keyPressed})の頭で先に受ける(mixin
 *       {@code emi_search_history.EmiScreenManagerHistoryMixin})。ホイールは画面の出来事が先に来るので、そちらで受ける。</li>
 *   <li>記すのは、Enter・欄の外を押したとき・欄から focus が外れたとき・欄に focus があるまま画面を閉じたとき
 *       (EMI の欄は画面をまたいで語が残るので、focus の無いまま閉じるたびには記さない)。</li>
 * </ul>
 */
public final class EmiSearchHistory {
    private static SearchHistoryOverlay overlay;
    /** 前の tick に EMI の検索欄に focus があったか。 */
    private static boolean focused;

    private EmiSearchHistory() {
    }

    public static void init(FeatureContext context) throws Exception {
        // 件数の設定は AE2 の端末の履歴の機能の欄のもの(AE2 の機能が切れていても画面に出す。同じものの登録は置き換え)
        Settings.client().register(SearchHistorySettings.COLLAPSED_ROWS);
        Settings.client().register(SearchHistorySettings.VISIBLE_ROWS);
        Settings.client().register(SearchHistorySettings.KEEP);
        SearchHistoryOverlay.loadStore();
        overlay = new SearchHistoryOverlay(SearchFeatures.EMI_SEARCH_HISTORY, "", List.of(new SearchHistoryOverlay.Host() {
            @Override
            public EditBox field(Screen screen) {
                return EmiBridge.searchField();
            }

            @Override
            public void apply(Screen screen, EditBox field, String text) {
                EmiBridge.setSearchText(text);
            }

            @Override
            public HistoryLayout.Mode mode() {
                return HistoryLayout.Mode.EMI;
            }

            @Override
            public boolean recordOnClose() {
                return false;
            }
        }), false);
        overlay.register();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> guard(EmiSearchHistory::watch));
    }

    private static void guard(Runnable body) {
        Features.guard(SearchFeatures.EMI_SEARCH_HISTORY, body);
    }

    /** EMI の検索欄から focus が外れたら、その語を記す(どの画面でも)。 */
    private static void watch() {
        boolean now = EmiBridge.isSearchFocused();
        if (focused && !now && overlay != null) {
            overlay.record(EmiBridge.getSearchText());
        }
        focused = now;
    }

    /** EMI が押したのを扱う前に呼ばれる(mixin)。ウィンドウで扱ったら true。 */
    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        Screen screen = Minecraft.getInstance().screen;
        return overlay != null && screen != null && overlay.click(screen, mouseX, mouseY, button);
    }

    /** EMI がキーを扱う前に呼ばれる(mixin)。ウィンドウで扱ったら true。 */
    public static boolean onKeyPressed(int keyCode) {
        Screen screen = Minecraft.getInstance().screen;
        return overlay != null && screen != null && overlay.key(screen, keyCode);
    }
}
