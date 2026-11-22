package gomacraft.utility.search.client;

import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.search.SearchHistoryStore;
import gomacraft.utility.settings.Settings;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * 検索欄の履歴の窓(AE2 の端末とコンパスで共通 —— goma-org/gomabase#2295。作りの参考は AE2 Recent Search ——
 * コードは写していない)。
 *
 * <ul>
 *   <li>検索欄に focus があるあいだ、欄の<b>上</b>に履歴を出す(端末の品の欄を隠さない)。欄に近い下から、お気に入り →
 *       新しい順に上へ積む。上が狭いときは、端末なら左の外、端末でない画面(コンパス)なら欄の下に出す。</li>
 *   <li>欄が空のときは最近の数件だけ出し、一覧にホバーすると広げる。入力中は入力に合う語だけを出し、合うものが無ければ出さない。</li>
 *   <li>一覧の上でホイールを回すと古いものへ遡る。</li>
 *   <li>記すのは、Enter・欄の外を押したとき・画面を閉じたとき(打つ 1 字ごとには記さない)。</li>
 *   <li>左で使う、右で消す、Shift + 左か中でお気に入りを付け外し。↑↓で選び、Enter で使う。操作の案内は一覧の上の端に出す。</li>
 *   <li>履歴は手元の {@code config/gomacraft_utility/search_history.json} に置く(サーバには送らない)。ファイルは 1 つで、
 *       窓ごとに別の帳({@code scope})を持つ(品の語とバイオームの語を混ぜない)。</li>
 * </ul>
 */
public final class SearchHistoryOverlay {
    /** 欄を持つ画面と、語を使う道(AE2 の端末・コンパス)。 */
    public interface Host {
        /** この画面の検索欄(この窓の画面でなければ null)。 */
        EditBox field(Screen screen);

        /** 履歴の語で検索する。 */
        void apply(Screen screen, EditBox field, String text);
    }

    private static final int ROW = 11;
    private static final int PAD = 2;
    private static final int MAX_WIDTH = 220;
    private static final String FAVORITE_MARK = "★ ";
    /** 端末の左の外へ出すときに空ける幅(AE2 の端末の左の道具の列の分)。 */
    private static final int SIDE_GAP = 24;
    /** 端末の左の外へ出すのに要る幅(これより狭ければ、上の端に寄せてでも欄の上に出す)。 */
    private static final int MIN_SIDE_WIDTH = 60;
    private static final int MAX_WRITE_FAILURES = 3;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GomacraftUtility.MOD_ID);

    // ファイルは 1 つ(窓が 2 つあっても同じ帳を読み書きする)
    private static SearchHistoryStore store;
    private static Path file;
    private static int writeFailures;

    private final String feature;
    private final String scope;
    private final List<Host> hosts;
    /** ↑↓で選んだ行(入力に合う一覧の中の位置。-1 は選んでいない)。 */
    private int selected = -1;
    /** 前に見た欄の値(変わったら選んだ行と先頭の位置を戻す)。 */
    private String lastValue = "";
    /** 一覧の先頭に出している位置(ホイールで遡る)。 */
    private int scroll;
    /** 前の描画で広げていたか(広げた一覧の中にマウスがある間は広げたままにする)。 */
    private boolean expanded;
    /** 前の描画で一覧を欄の上に(下から上へ)積んだか。↑↓とホイールの向きを、見た目の向きに合わせる。 */
    private boolean lastUpward = true;

    /**
     * @param feature この窓を持つ機能(guard と FAILED の宛先)
     * @param scope 帳の名前(空ならプレイヤーの帳そのまま —— AE2 の端末の前からの帳)
     */
    public SearchHistoryOverlay(String feature, String scope, List<Host> hosts) {
        this.feature = feature;
        this.scope = scope;
        this.hosts = List.copyOf(hosts);
    }

    /** 出す件数(設定の画面の値。すぐ効く —— 件数の設定は AE2 の端末の履歴の機能のもので、コンパスも同じ値を使う)。 */
    private static int collapsedRows() {
        return Settings.client().get(Ae2SearchHistory.COLLAPSED_ROWS);
    }

    private static int visibleRows() {
        return Math.max(collapsedRows(), Settings.client().get(Ae2SearchHistory.VISIBLE_ROWS));
    }

    /** 履歴のファイルを読む(1 回だけ。読めないファイルは上書きせずに、呼んだ機能を止める)。 */
    static synchronized void loadStore() {
        if (store != null) {
            return;
        }
        file = FMLPaths.CONFIGDIR.get().resolve(GomacraftUtility.MOD_ID).resolve("search_history.json");
        try {
            // 持つ件数は、設定を読む前の既定の値で読み、記すたびに今の設定の値へ合わせる(setMaxRecent)
            store = SearchHistoryStore.load(file, Ae2SearchHistory.KEEP.defaultValue());
        } catch (Exception e) {
            throw new IllegalStateException("search history unreadable: " + FeatureRegistry.describe(e), e);
        }
    }

    /** 画面の出来事を聞く。 */
    public void register() {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, event -> guard(() -> render(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class, event -> guard(() -> onKey(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.CharacterTyped.Post.class, event -> guard(() -> {
            if (field(event.getScreen()) != null) {
                selected = -1;
                scroll = 0;
            }
        }));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Pre.class, event -> guard(() -> onClick(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseScrolled.Pre.class, event -> guard(() -> onScroll(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Closing.class, event -> guard(() -> onClose(event.getScreen())));
    }

    private void guard(Runnable body) {
        Features.guard(feature, body);
    }

    private EditBox field(Screen screen) {
        for (Host host : hosts) {
            EditBox field = host.field(screen);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private Host host(Screen screen) {
        for (Host host : hosts) {
            if (host.field(screen) != null) {
                return host;
            }
        }
        return null;
    }

    private String player() {
        var user = Minecraft.getInstance().getUser();
        String who = user.getProfileId() != null ? user.getProfileId().toString() : user.getName();
        return scope.isEmpty() ? who : who + "#" + scope;
    }

    /** 語を記す(ほかの機能から呼ぶときも guard の中で)。 */
    public void record(String text) {
        if (store == null) {
            return;
        }
        store.setMaxRecent(Settings.client().get(Ae2SearchHistory.KEEP));
        if (store.record(player(), text)) {
            save();
        }
    }

    /**
     * 書く。一時の失敗(ほかの道具がファイルを掴んでいる、など)は、続けて {@link #MAX_WRITE_FAILURES} 回までは警告だけに
     * して次の変化で書き直す(履歴は手元の覚えにあるので失わない)。続けて越えたら、呼んだ機能を FAILED にする。
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

    /**
     * 入力に合う語(出す順)。欄の値が変わったら(打つ・貼り付け・Backspace など、どの道でも)、選んだ行と先頭の位置を戻す。
     */
    private List<SearchHistoryStore.Entry> candidates(EditBox field) {
        String value = field.getValue();
        if (!value.equals(lastValue)) {
            lastValue = value;
            selected = -1;
            scroll = 0;
        }
        return store.matching(player(), value);
    }

    /** 一覧を出す位置と大きさ。{@code upward} は欄に近い先頭を一番下に置く形。 */
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
    private Box box(Screen screen, EditBox field, List<SearchHistoryStore.Entry> all, boolean wide) {
        if (field == null || !field.isVisible() || !field.isFocused() || all.isEmpty()) {
            return null;
        }
        boolean typing = !field.getValue().isBlank();
        int count = typing || wide ? visibleRows() : collapsedRows();
        boolean hint = wide;
        Font font = Minecraft.getInstance().font;
        // 欄の上の空きに収まる行の数(画面の上の端を越えない)。上が狭いときは、端末なら左の外、端末でない画面なら欄の下へ
        int room = (field.getY() - 1 - PAD * 2 - (hint ? ROW : 0)) / ROW;
        boolean container = screen instanceof AbstractContainerScreen<?>;
        int leftEdge = screen instanceof AbstractContainerScreen<?> c ? c.getGuiLeft() - SIDE_GAP : 0;
        boolean upward = room >= Math.min(count, 3) || (container && leftEdge < MIN_SIDE_WIDTH);
        int belowTop = field.getY() + field.getHeight() + 1;
        if (upward) {
            count = Math.max(1, Math.min(count, room));
        } else {
            int start = container ? field.getY() : belowTop;
            int below = (screen.height - start - PAD * 2 - (hint ? ROW : 0)) / ROW;
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
        } else if (container) {
            width = Math.min(width, leftEdge - 1);
            x = leftEdge - width;
            top = field.getY();
        } else {
            x = Math.max(0, Math.min(field.getX(), screen.width - width - 1));
            top = belowTop;
        }
        int rowsTop = top + PAD + (hint ? ROW : 0);
        return new Box(x, top, width, rowsTop, rows, scroll, hint, upward);
    }

    /** 今の描画の一覧(広げるかは、前の形とマウスの位置で決める)。 */
    private Box currentBox(Screen screen, EditBox field, double mouseX, double mouseY) {
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

    private void render(ScreenEvent.Render.Post event) {
        EditBox field = field(event.getScreen());
        Box box = currentBox(event.getScreen(), field, event.getMouseX(), event.getMouseY());
        if (box == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int hovered = box.indexAt(event.getMouseX(), event.getMouseY());
        g.pose().pushPose();
        // 画面の品とツールチップの上に出す
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

    private void onKey(ScreenEvent.KeyPressed.Pre event) {
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
                // 画面が Enter で欄の focus を外す。その前に記す
                record(field.getValue());
            }
            selected = -1;
            scroll = 0;
        }
    }

    private void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        EditBox field = field(event.getScreen());
        Box box = currentBox(event.getScreen(), field, event.getMouseX(), event.getMouseY());
        if (box == null || !box.contains(event.getMouseX(), event.getMouseY())) {
            return;
        }
        // 欄の上に積むときは上が古い: ホイールを上へ回すと古いものへ遡る(左の外・欄の下に出すときは逆)
        boolean older = (event.getScrollDeltaY() > 0) == box.upward();
        scroll += older ? 1 : -1;
        if (scroll < 0) {
            scroll = 0;
        }
        event.setCanceled(true);
    }

    private void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
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
    private void use(Screen screen, EditBox field, String text) {
        Host host = host(screen);
        if (host != null) {
            host.apply(screen, field, text);
        }
        record(text);
        field.setFocused(false);
        screen.setFocused(null);
        scroll = 0;
    }

    private void onClose(Screen screen) {
        EditBox field = field(screen);
        if (field != null && field.isVisible()) {
            record(field.getValue());
        }
        selected = -1;
        scroll = 0;
        expanded = false;
    }
}
