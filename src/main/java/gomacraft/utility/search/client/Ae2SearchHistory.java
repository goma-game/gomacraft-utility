package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.mixin.ae2_search_history.MEStorageScreenHistoryAccessor;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.SearchHistoryStore;
import gomacraft.utility.settings.ClientSettings;
import gomacraft.utility.settings.Settings;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * AE2 の端末の検索語の履歴(goma-org/gomabase#2295。作りの参考は AE2 Recent Search —— コードは写していない)。
 *
 * <ul>
 *   <li>検索欄に focus があるあいだ、欄の<b>上</b>に履歴を出す(端末の品の欄を隠さない)。欄に近い下から、お気に入り →
 *       新しい順に上へ積む。</li>
 *   <li>欄が空のときは最近の数件だけ出し、一覧にホバーすると広げる。入力中は入力に合う語だけを出し、合うものが無ければ出さない。</li>
 *   <li>一覧の上でホイールを回すと古いものへ遡る。出す件数(空のとき・広げたとき)と持つ件数は設定の画面
 *       ({@code ClientSettings}。既定は 3・10・100)で変えられ、すぐ効く(持つ件数は次に記すときから)。</li>
 *   <li>記すのは、Enter・欄の外を押したとき・端末を閉じたとき・F キーで送ったとき、と EMI の検索欄から focus が外れたとき
 *       (AE2 と EMI の履歴を 1 つにする。打つ 1 字ごとには記さない)。</li>
 *   <li>左で使う、右で消す、Shift + 左か中でお気に入りを付け外し。↑↓で選び、Enter で使う。操作の案内は一覧の上の端に出す
 *       (マウスの位置に出すと、端末の品のツールチップと重なる)。</li>
 *   <li>履歴は手元の {@code config/gomacraft_utility/search_history.json} に、プレイヤーごとに置く(サーバには送らない)。</li>
 * </ul>
 */
public final class Ae2SearchHistory {
    private static final int ROW = 11;
    private static final int PAD = 2;
    private static final int MAX_WIDTH = 220;
    private static final String FAVORITE_MARK = "★ ";
    /** 端末の左の外へ出すときに空ける幅(AE2 の端末の左の道具の列の分)。 */
    private static final int SIDE_GAP = 24;
    /** 端末の左の外へ出すのに要る幅(これより狭ければ、上の端に寄せてでも欄の上に出す)。 */
    private static final int MIN_SIDE_WIDTH = 60;

    /** 欄が空でホバーしていないときに出す件数(設定の画面で変えられる。すぐ効く)。 */
    public static final ClientSettings.Int COLLAPSED_ROWS =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "collapsed_rows", 3, 1, 20);
    /** 広げたとき(ホバー・入力中)に一度に出す件数。それより古いものはホイールで遡る。 */
    public static final ClientSettings.Int VISIBLE_ROWS =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "visible_rows", 10, 1, 30);
    /** 持つ件数(お気に入りは数えない。古いものから消す)。 */
    public static final ClientSettings.Int KEEP =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "keep", 100, 10, 1000);

    private static final int MAX_WRITE_FAILURES = 3;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GomacraftUtility.MOD_ID);

    private static SearchHistoryStore store;
    private static int writeFailures;
    private static Path file;
    /** ↑↓で選んだ行(入力に合う一覧の中の位置。-1 は選んでいない)。 */
    private static int selected = -1;
    /** 前に見た欄の値(変わったら選んだ行と先頭の位置を戻す)。 */
    private static String lastValue = "";
    /** 一覧の先頭に出している位置(ホイールで遡る)。 */
    private static int scroll;
    /** 前の描画で広げていたか(広げた一覧の中にマウスがある間は広げたままにする)。 */
    private static boolean expanded;
    /** 前の tick に EMI の検索欄に focus があったか。 */
    private static boolean emiFocused;
    /** 前の描画で一覧を欄の上に(下から上へ)積んだか。↑↓とホイールの向きを、見た目の向きに合わせる。 */
    private static boolean lastUpward = true;

    private Ae2SearchHistory() {
    }

    public static void init(FeatureContext context) throws Exception {
        Settings.client().register(COLLAPSED_ROWS);
        Settings.client().register(VISIBLE_ROWS);
        Settings.client().register(KEEP);
        file = FMLPaths.CONFIGDIR.get().resolve(GomacraftUtility.MOD_ID).resolve("search_history.json");
        try {
            // 持つ件数は、設定を読む前の既定の値で読み、記すたびに今の設定の値へ合わせる(setMaxRecent)
            store = SearchHistoryStore.load(file, KEEP.defaultValue());
        } catch (Exception e) {
            // 読めないファイルは上書きしない(人が直せるように残す)。この機能だけを止める
            throw new IllegalStateException("search history unreadable: " + FeatureRegistry.describe(e), e);
        }
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, event -> guard(() -> render(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class, event -> guard(() -> onKey(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.CharacterTyped.Post.class, event -> guard(() -> {
            selected = -1;
            scroll = 0;
        }));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Pre.class, event -> guard(() -> onClick(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseScrolled.Pre.class, event -> guard(() -> onScroll(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Closing.class, event -> guard(() -> onClose(event.getScreen())));
        if (ModList.get().isLoaded("emi")) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> guard(Ae2SearchHistory::watchEmi));
        }
    }

    private static void guard(Runnable body) {
        Features.guard(SearchFeatures.AE2_SEARCH_HISTORY, body);
    }

    /** ほかの機能(F キー・EMI から写すキー)が検索語を入れたときに記す。履歴の機能が切れていれば何もしない。 */
    static void recordFromOtherFeature(String text) {
        guard(() -> record(text));
    }

    private static EditBox field(Screen screen) {
        if (!(screen instanceof MEStorageScreen<?>)) {
            return null;
        }
        return ((MEStorageScreenHistoryAccessor) screen).gomacraftUtility$historySearchField();
    }

    private static String player() {
        var user = Minecraft.getInstance().getUser();
        return user.getProfileId() != null ? user.getProfileId().toString() : user.getName();
    }

    private static void record(String text) {
        if (store == null) {
            return;
        }
        store.setMaxRecent(Settings.client().get(KEEP));
        if (store.record(player(), text)) {
            save();
        }
    }

    /**
     * 書く。一時の失敗(ほかの道具がファイルを掴んでいる、など)は、続けて {@link #MAX_WRITE_FAILURES} 回までは警告だけに
     * して次の変化で書き直す(履歴は手元の覚えにあるので失わない)。続けて越えたら、この機能を FAILED にする。
     */
    private static void save() {
        try {
            store.save(file);
            writeFailures = 0;
        } catch (Exception e) {
            writeFailures++;
            if (writeFailures >= MAX_WRITE_FAILURES) {
                throw new IllegalStateException("search history unwritable: " + FeatureRegistry.describe(e), e);
            }
            LOGGER.warn("gomacraft_utility: 検索語の履歴を書けない(続けて {} 回目。次の変化で書き直す): {}",
                    writeFailures, FeatureRegistry.describe(e));
        }
    }

    /** EMI の検索欄から focus が外れたら、その語を記す(どの画面でも)。 */
    private static void watchEmi() {
        boolean focused = EmiBridge.isSearchFocused();
        if (emiFocused && !focused) {
            record(EmiBridge.getSearchText());
        }
        emiFocused = focused;
    }

    /**
     * 入力に合う語(出す順)。欄の値が変わったら(打つ・貼り付け・Backspace など、どの道でも)、選んだ行と先頭の位置を戻す。
     */
    private static List<SearchHistoryStore.Entry> candidates(EditBox field) {
        String value = field.getValue();
        if (!value.equals(lastValue)) {
            lastValue = value;
            selected = -1;
            scroll = 0;
        }
        return store.matching(player(), value);
    }

    /** 一覧を出す位置と大きさ。 */
    private record Box(int x, int top, int width, int rowsTop, List<SearchHistoryStore.Entry> rows, int first,
                       boolean hint, boolean upward) {
        int bottom() {
            return rowsTop + rows.size() * ROW + PAD;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x - 1 && mouseX < x + width + 1 && mouseY >= top - 1 && mouseY < bottom() + 1;
        }

        /** 一覧の i 番目(0 = 先頭 = 欄に近い)の行の上端。欄の上に出すときは、先頭を一番下に置く。 */
        int rowY(int i) {
            return rowsTop + (upward ? rows.size() - 1 - i : i) * ROW;
        }

        /** マウスの下の行の、入力に合う一覧の中の位置(行の上でなければ -1)。 */
        int indexAt(double mouseX, double mouseY) {
            if (mouseX < x || mouseX >= x + width || mouseY < rowsTop || mouseY >= rowsTop + rows.size() * ROW) {
                return -1;
            }
            int fromTop = (int) ((mouseY - rowsTop) / ROW);
            return first + (upward ? rows.size() - 1 - fromTop : fromTop);
        }
    }

    /** 欄の上に出す一覧(出さないなら null)。{@code wide} は広げた形(ホバー・入力中)。 */
    private static Box box(Screen screen, EditBox field, List<SearchHistoryStore.Entry> all, boolean wide) {
        if (field == null || !field.isVisible() || !field.isFocused() || all.isEmpty()) {
            return null;
        }
        boolean typing = !field.getValue().isBlank();
        int collapsedRows = Settings.client().get(COLLAPSED_ROWS);
        int visibleRows = Math.max(collapsedRows, Settings.client().get(VISIBLE_ROWS));
        int count = typing || wide ? visibleRows : collapsedRows;
        boolean hint = wide;
        Font font = Minecraft.getInstance().font;
        // 欄の上の空きに収まる行の数(画面の上の端を越えない)。端末が縦に長い形で上が狭いときは、端末の左の外へ出す
        int room = (field.getY() - 1 - PAD * 2 - (hint ? ROW : 0)) / ROW;
        int leftEdge = screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> c
                ? c.getGuiLeft() - SIDE_GAP : 0;
        boolean upward = room >= Math.min(count, 3) || leftEdge < MIN_SIDE_WIDTH;
        if (upward) {
            count = Math.max(1, Math.min(count, room));
        } else {
            int below = (screen.height - field.getY() - PAD * 2 - (hint ? ROW : 0)) / ROW;
            count = Math.max(1, Math.min(count, below));
        }
        // 候補は貼り付け・Backspace・削除で減る。選んだ行と先頭の位置を、今の候補の数で丸める
        if (selected >= all.size()) {
            selected = -1;
        }
        if (selected >= 0) {
            if (selected < scroll) {
                scroll = selected;
            } else if (selected >= scroll + count) {
                scroll = selected - count + 1;
            }
        }
        int maxScroll = Math.max(0, all.size() - count);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        List<SearchHistoryStore.Entry> rows = all.subList(scroll, Math.min(all.size(), scroll + count));
        int width = field.getWidth();
        for (SearchHistoryStore.Entry entry : rows) {
            width = Math.max(width, font.width(label(entry)) + PAD * 2 + 2);
        }
        if (hint) {
            width = Math.max(width, font.width(hintText()) + PAD * 2);
        }
        width = Math.min(width, Math.max(field.getWidth(), MAX_WIDTH));
        int height = rows.size() * ROW + PAD * 2 + (hint ? ROW : 0);
        int x;
        int top;
        if (upward) {
            x = Math.max(0, Math.min(field.getX(), screen.width - width - 1));
            top = Math.max(0, field.getY() - 1 - height);
        } else {
            width = Math.min(width, leftEdge - 1);
            x = leftEdge - width;
            top = field.getY();
        }
        int rowsTop = top + PAD + (hint ? ROW : 0);
        return new Box(x, top, width, rowsTop, rows, scroll, hint, upward);
    }

    /** 今の描画の一覧(広げるかは、前の形とマウスの位置で決める)。 */
    private static Box currentBox(Screen screen, EditBox field, double mouseX, double mouseY) {
        if (field == null || store == null) {
            return null;
        }
        List<SearchHistoryStore.Entry> all = candidates(field);
        Box wide = box(screen, field, all, true);
        if (wide == null) {
            expanded = false;
            return null;
        }
        Box narrow = box(screen, field, all, false);
        boolean hover = narrow.contains(mouseX, mouseY) || (expanded && wide.contains(mouseX, mouseY));
        expanded = hover;
        Box box = hover ? wide : narrow;
        lastUpward = box.upward();
        return box;
    }

    private static Component hintText() {
        return Component.translatable("gomacraft_utility.search_history.hint");
    }

    private static String label(SearchHistoryStore.Entry entry) {
        return entry.favorite() ? FAVORITE_MARK + entry.text() : entry.text();
    }

    private static void render(ScreenEvent.Render.Post event) {
        EditBox field = field(event.getScreen());
        Box box = currentBox(event.getScreen(), field, event.getMouseX(), event.getMouseY());
        if (box == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int hovered = box.indexAt(event.getMouseX(), event.getMouseY());
        g.pose().pushPose();
        // 端末の品とツールチップの上に出す
        g.pose().translate(0, 0, 400);
        g.fill(box.x() - 1, box.top() - 1, box.x() + box.width() + 1, box.bottom() + 1, 0xFF8B8B8B);
        g.fill(box.x(), box.top(), box.x() + box.width(), box.bottom(), 0xF0101010);
        if (box.hint()) {
            String hint = font.plainSubstrByWidth(hintText().getString(), box.width() - PAD * 2);
            g.drawString(font, hint, box.x() + PAD, box.top() + PAD + 2, 0xFF9A9A9A, false);
        }
        for (int i = 0; i < box.rows().size(); i++) {
            SearchHistoryStore.Entry entry = box.rows().get(i);
            int index = box.first() + i;
            int y = box.rowY(i);
            if (index == selected || index == hovered) {
                g.fill(box.x() + 1, y, box.x() + box.width() - 1, y + ROW, 0x60FFFFFF);
            }
            String text = font.plainSubstrByWidth(label(entry), box.width() - PAD * 2);
            g.drawString(font, text, box.x() + PAD, y + 2, entry.favorite() ? 0xFFFFD866 : 0xFFE0E0E0, false);
        }
        g.pose().popPose();
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre event) {
        EditBox field = field(event.getScreen());
        if (field == null || !field.isVisible() || !field.isFocused() || store == null) {
            return;
        }
        int key = event.getKeyCode();
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            // 見た目の向きに合わせる: 欄の上に積むときは、↑で欄から遠い(古い)方へ動く
            int size = candidates(field).size();
            if (size == 0) {
                return;
            }
            if ((key == GLFW.GLFW_KEY_UP) == lastUpward) {
                selected = selected + 1 >= size ? 0 : selected + 1;
            } else {
                selected = selected <= 0 ? size - 1 : selected - 1;
            }
            event.setCanceled(true);
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            List<SearchHistoryStore.Entry> all = candidates(field);
            if (selected >= 0 && selected < all.size()) {
                use(event.getScreen(), field, all.get(selected).text());
                event.setCanceled(true);
            } else {
                // AE2 が Enter で欄の focus を外す。その前に記す
                record(field.getValue());
            }
            selected = -1;
            scroll = 0;
        }
    }

    private static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        EditBox field = field(event.getScreen());
        Box box = currentBox(event.getScreen(), field, event.getMouseX(), event.getMouseY());
        if (box == null || !box.contains(event.getMouseX(), event.getMouseY())) {
            return;
        }
        // 欄の上に積むときは上が古い: ホイールを上へ回すと古いものへ遡る(左の外に出すときは逆)
        boolean older = (event.getScrollDeltaY() > 0) == box.upward();
        scroll += older ? 1 : -1;
        if (scroll < 0) {
            scroll = 0;
        }
        event.setCanceled(true);
    }

    private static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        EditBox field = field(event.getScreen());
        Box box = currentBox(event.getScreen(), field, event.getMouseX(), event.getMouseY());
        if (box == null) {
            return;
        }
        int index = box.indexAt(event.getMouseX(), event.getMouseY());
        if (index < 0 && box.contains(event.getMouseX(), event.getMouseY())) {
            // 一覧の余白・案内の行。下の枠へ通さない
            event.setCanceled(true);
            return;
        }
        if (index < 0) {
            if (!field.isMouseOver(event.getMouseX(), event.getMouseY())) {
                // 欄の外を押した(focus が外れる)。今の語を記す
                record(field.getValue());
                selected = -1;
                scroll = 0;
            }
            return;
        }
        String text = box.rows().get(index - box.first()).text();
        int button = event.getButton();
        boolean shift = Screen.hasShiftDown();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (store.remove(player(), text)) {
                save();
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shift)) {
            if (store.toggleFavorite(player(), text)) {
                save();
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            use(event.getScreen(), field, text);
        }
        selected = -1;
        event.setCanceled(true);
    }

    /** 履歴の語で検索し、欄の focus を外す(結果をすぐ見られるように)。 */
    private static void use(Screen screen, EditBox field, String text) {
        Ae2Search.apply(field, text);
        record(text);
        field.setFocused(false);
        screen.setFocused(null);
        scroll = 0;
    }

    private static void onClose(Screen screen) {
        EditBox field = field(screen);
        if (field != null && field.isVisible()) {
            record(field.getValue());
        }
        selected = -1;
        scroll = 0;
        expanded = false;
    }
}
