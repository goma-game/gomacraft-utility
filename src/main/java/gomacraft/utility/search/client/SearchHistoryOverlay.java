package gomacraft.utility.search.client;

import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.search.FocusRecorder;
import gomacraft.utility.search.HistoryLayout;
import gomacraft.utility.search.SearchHistorySettings;
import gomacraft.utility.search.SearchHistoryStore;
import gomacraft.utility.settings.Settings;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * 検索欄の履歴のウィンドウ(AE2 の端末・EMI の検索欄・コンパスで共通 —— goma-org/gomabase#2295。作りの参考は
 * AE2 Recent Search —— コードは写していない)。
 *
 * <ul>
 *   <li>検索欄に focus があるあいだ出す。置き方は欄ごとに {@link HistoryLayout}(AE2 の端末は欄の左の見出しの所、
 *       EMI は欄の左、コンパスは欄の右の空き —— 狭ければボタンの行の下)。小さい形で出し、カーソルを合わせると広げる。入力中は入力に合う語だけを出し、合うものが無ければ
 *       出さない。</li>
 *   <li>出しているあいだは、ウィンドウと欄の上のツールチップ(AE2 の「名前で検索」、ウィンドウの下のアイテムのもの)を
 *       出さない({@link RenderTooltipEvent.Pre} を止める)。描く順(z)で上に出すのではなく止めるのは、ツールチップが
 *       カーソルの位置で大きさも向きも変わり、重ならない置き方を決められないため。</li>
 *   <li>一覧の上でホイールを回すと古いものへ遡る。</li>
 *   <li>記すのは、欄の focus が外れたとき(Enter・欄の外を押す、など)・Enter・履歴の語を使ったとき・画面を閉じたとき
 *       (打つ 1 字ごとには記さない。契機の決め方は {@link FocusRecorder})。</li>
 *   <li>幅は字の長さで変えない。はみ出す語は「…」で切り、カーソルを合わせた行・↑↓で選んだ行は横に流して全文を見せる。</li>
 *   <li>左で使う、右で消す、Shift + 左か中でお気に入りを付け外し。↑↓で選び、Enter で使う。操作の案内は広げたときに出す。</li>
 *   <li>履歴は手元の {@code config/gomacraft_utility/search_history.json} に置く(サーバには送らない)。ファイルは 1 つで、
 *       ウィンドウごとに別の帳({@code scope})を持つ(アイテムの語とバイオームの語を混ぜない。AE2 と EMI は同じ帳)。</li>
 * </ul>
 */
public final class SearchHistoryOverlay {
    /** 欄を持つ画面と、語を使う道。 */
    public interface Host {
        /** この画面の検索欄(この画面に無ければ null)。 */
        EditBox field(Screen screen);

        /** 履歴の語で検索する。 */
        void apply(Screen screen, EditBox field, String text);

        /** 置き方。 */
        default HistoryLayout.Mode mode() {
            return HistoryLayout.Mode.AROUND;
        }

        /** 欄の見た目の枠(AE2 の欄は枠が字の外へ 2 ずつ広い)。 */
        default HistoryLayout.Rect bounds(EditBox field) {
            return new HistoryLayout.Rect(field.getX(), field.getY(), field.getWidth(), field.getHeight());
        }

        /** 欄の右に出す形({@link HistoryLayout.Mode#RIGHT})の、小さい形の右の端(右の空きの終わり)。 */
        default int sideRight(Screen screen) {
            return screen.width;
        }

        /** 画面を閉じたときに欄の語を記すか(EMI の欄は画面をまたいで語が残るので、閉じるたびには記さない)。 */
        default boolean recordOnClose() {
            return true;
        }
    }

    private static final int ROW = HistoryLayout.ROW;
    private static final int PAD = HistoryLayout.PAD;
    private static final String FAVORITE_MARK = "★ ";
    private static final String ELLIPSIS = "…";
    private static final int MAX_WRITE_FAILURES = 3;
    /** 描く高さ(ツールチップの 400 より上)。 */
    private static final int Z = 500;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GomacraftUtility.MOD_ID);

    /** 作ったウィンドウ(ほかの機能が「ウィンドウの上か」を問うため —— {@link #isOverShownWindow})。 */
    private static final List<SearchHistoryOverlay> ALL = new java.util.concurrent.CopyOnWriteArrayList<>();

    // ファイルは 1 つ(ウィンドウがいくつあっても同じ帳を読み書きする)
    private static SearchHistoryStore store;
    private static Path file;

    private final String feature;
    private final String scope;
    private final List<Host> hosts;
    /** 押す・キーを画面の出来事で受けるか(偽なら、呼ぶ側が {@link #click}・{@link #key} を先に呼ぶ —— EMI)。 */
    private final boolean eventInput;
    /** 続けて書けなかった回数(ウィンドウごと —— FAILED にするのは、書こうとしたウィンドウの機能だけ)。 */
    private int writeFailures;
    /** ↑↓で選んだ行(入力に合う一覧の中の位置。-1 は選んでいない)。 */
    private int selected = -1;
    /** 前に見た欄の値(変わったら選んだ行と先頭の位置を戻す)。 */
    private String lastValue = "";
    /** 一覧の先頭に出している位置(ホイールで遡る)。 */
    private int scroll;
    /** 前の描画で広げていたか(広げた一覧の中にマウスがある間は広げたままにする)。 */
    private boolean expanded;
    /** 前の描画で一覧を下から上へ積んだか。↑↓とホイールの向きを、見た目の向きに合わせる。 */
    private boolean lastUpward = true;
    /** 前の描画で出したウィンドウと欄の枠と画面(ツールチップを止めるのに使う。出していなければ null)。 */
    private Box shown;
    private HistoryLayout.Rect shownField;
    private Screen shownScreen;
    /** 横に流している行の語と、流し始めた時刻(行が変われば頭から流す)。 */
    private String marqueeText;
    private long marqueeStart;
    /** 語を記す契機(focus が外れた・閉じた —— 欄の様子で決める)。 */
    private final FocusRecorder recorder = new FocusRecorder();
    /** このウィンドウで押したので、続く離す操作も画面へ通さない。 */
    private boolean swallowRelease;

    /**
     * @param feature    このウィンドウを持つ機能(guard と FAILED の宛先)
     * @param scope      帳の名前(空ならプレイヤーの帳そのまま —— AE2 の端末と EMI の帳)
     * @param eventInput 押す・キーを画面の出来事で受けるか
     */
    public SearchHistoryOverlay(String feature, String scope, List<Host> hosts, boolean eventInput) {
        this.feature = feature;
        this.scope = scope;
        this.hosts = List.copyOf(hosts);
        this.eventInput = eventInput;
        ALL.add(this);
    }

    /**
     * 今の画面に出ているどれかのウィンドウの上か(前の描画の形で見る)。⚠ EMI は押すのを画面の出来事より先に自分の入口で
     * 受け、この Mod のほかの機能(EMI のアイテムの中クリック)もそこに当たる。ウィンドウが EMI の一覧やアイテムの欄に
     * 重なるので、その機能はウィンドウの上なら譲る(当てる順に依らない形)。
     */
    public static boolean isOverShownWindow(double mouseX, double mouseY) {
        Screen screen = Minecraft.getInstance().screen;
        for (SearchHistoryOverlay overlay : ALL) {
            Box box = overlay.shown;
            if (box != null && screen != null && overlay.shownScreen == screen && box.contains(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #isOverShownWindow} を今のマウスの位置で(キーの操作から問うとき)。 */
    public static boolean isMouseOverShownWindow() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double x = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        return isOverShownWindow(x, y);
    }

    public SearchHistoryOverlay(String feature, String scope, List<Host> hosts) {
        this(feature, scope, hosts, true);
    }

    /** 出す件数(設定の画面の値。すぐ効く —— 件数の設定は AE2 の端末の履歴の機能のもので、ほかのウィンドウも同じ値を使う)。 */
    private static int collapsedRows() {
        return Settings.client().get(SearchHistorySettings.COLLAPSED_ROWS);
    }

    private static int visibleRows() {
        return Math.max(collapsedRows(), Settings.client().get(SearchHistorySettings.VISIBLE_ROWS));
    }

    /** 履歴のファイルを読む(1 回だけ。読めないファイルは上書きせずに、呼んだ機能を止める)。 */
    static synchronized void loadStore() {
        if (store != null) {
            return;
        }
        file = FMLPaths.CONFIGDIR.get().resolve(GomacraftUtility.MOD_ID).resolve("search_history.json");
        try {
            // ⚠ 読むときは持つ件数の上限で読む(既定の 100 で読むと、100 より大きくした設定でも起動のたびに 100 件へ
            // 切られる —— #21 のレビュー)。記すたびに今の設定の値へ合わせる(setMaxRecent)
            store = SearchHistoryStore.load(file, SearchHistorySettings.KEEP.max());
        } catch (Exception e) {
            throw new IllegalStateException("search history unreadable: " + FeatureRegistry.describe(e), e);
        }
    }

    /** 画面の出来事を聞く。 */
    public void register() {
        // 描くのは最後(EMI が同じ出来事で描く欄より上に出す)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ScreenEvent.Render.Post.class,
                event -> guard(() -> render(event)));
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Pre.class, event -> guard(() -> onTooltip(event)));
        if (eventInput) {
            NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class, event -> guard(() -> {
                if (key(event.getScreen(), event.getKeyCode())) {
                    event.setCanceled(true);
                }
            }));
            NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Pre.class, event -> guard(() -> {
                if (click(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
                    event.setCanceled(true);
                }
            }));
        }
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonReleased.Pre.class, event -> guard(() -> {
            if (swallowRelease) {
                swallowRelease = false;
                event.setCanceled(true);
            }
        }));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.CharacterTyped.Post.class, event -> guard(() -> {
            if (field(event.getScreen()) != null) {
                selected = -1;
                scroll = 0;
            }
        }));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseScrolled.Pre.class, event -> guard(() -> onScroll(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Closing.class, event -> guard(() -> onClose(event.getScreen())));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> guard(this::watchFocus));
    }

    /** 欄の focus が外れたら、その語を記す(Enter・欄の外を押す・Tab など、どの道で外れても —— {@link FocusRecorder})。 */
    private void watchFocus() {
        Screen screen = Minecraft.getInstance().screen;
        EditBox field = screen == null ? null : field(screen);
        boolean present = field != null && field.isVisible();
        String text = recorder.observe(present, present && field.isFocused(), present ? field.getValue() : "");
        if (text != null) {
            record(text);
        }
    }

    private void guard(Runnable body) {
        Features.guard(feature, body);
    }

    private Host host(Screen screen) {
        for (Host host : hosts) {
            if (host.field(screen) != null) {
                return host;
            }
        }
        return null;
    }

    private EditBox field(Screen screen) {
        Host host = host(screen);
        return host == null ? null : host.field(screen);
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
        store.setMaxRecent(Settings.client().get(SearchHistorySettings.KEEP));
        if (store.record(player(), text)) {
            save();
        }
    }

    /**
     * 書く。一時の失敗(ほかの道具がファイルを掴んでいる、など)は、続けて {@link #MAX_WRITE_FAILURES} 回までは警告だけに
     * して次の変化で書き直す(履歴は手元の覚えにあるので失わない)。続けて越えたら、呼んだ機能を FAILED にする。
     */
    private void save() {
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
    private record Box(HistoryLayout.Rect rect, int rowsTop, List<SearchHistoryStore.Entry> rows, int first,
                       List<String> hint, int hintTop, boolean upward) {
        int x() {
            return rect.x();
        }

        int width() {
            return rect.width();
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= rect.x() - 1 && mouseX < rect.right() + 1
                    && mouseY >= rect.y() - 1 && mouseY < rect.bottom() + 1;
        }

        /** 一覧の i 番目(0 = 先頭 = 欄に近い)の行の上端。下から積むときは、先頭を一番下に置く。 */
        int rowY(int i) {
            return rowsTop + (upward ? rows.size() - 1 - i : i) * ROW;
        }

        /** マウスの下の行の、入力に合う一覧の中の位置(行の上でなければ -1)。 */
        int indexAt(double mouseX, double mouseY) {
            if (mouseX < rect.x() || mouseX >= rect.right() || mouseY < rowsTop
                    || mouseY >= rowsTop + rows.size() * ROW) {
                return -1;
            }
            int fromTop = (int) ((mouseY - rowsTop) / ROW);
            return first + (upward ? rows.size() - 1 - fromTop : fromTop);
        }
    }

    /** 出す一覧(出さないなら null)。{@code wide} は広げた形(ホバー)。 */
    private Box box(Screen screen, Host host, EditBox field, List<SearchHistoryStore.Entry> all, boolean wide) {
        if (field == null || !field.isVisible() || !field.isFocused() || all.isEmpty()) {
            return null;
        }
        boolean typing = !field.getValue().isBlank();
        int wanted = typing || wide ? visibleRows() : collapsedRows();
        List<String> hint = wide ? hintLines() : List.of();
        boolean container = screen instanceof AbstractContainerScreen<?>;
        int panelLeft = screen instanceof AbstractContainerScreen<?> c ? c.getGuiLeft() : 0;
        HistoryLayout.Placement p = HistoryLayout.place(new HistoryLayout.Input(host.mode(), screen.width,
                screen.height, host.bounds(field), panelLeft, host.sideRight(screen), container, wanted, hint.size(),
                wide));
        int count = p.rows();
        // 候補は貼り付け・Backspace・削除で減る。選んだ行と先頭の位置を、今の候補の数とこの形の行の数で丸める。
        // ⚠ ここでは scroll を書き換えない(小さい形と広げた形の両方を作るので、書き換えると選んだ行が動くたびに一覧が
        // 流れる)。実際に出す形の先頭を、呼んだ側が scroll に写す(currentBox)
        if (selected >= all.size()) {
            selected = -1;
        }
        int first = scroll;
        if (selected >= 0) {
            if (selected < first) {
                first = selected;
            } else if (selected >= first + count) {
                first = selected - count + 1;
            }
        }
        int maxScroll = Math.max(0, all.size() - count);
        first = Math.max(0, Math.min(first, maxScroll));
        List<SearchHistoryStore.Entry> rows = all.subList(first, Math.min(all.size(), first + count));
        // 行が候補より少なければ、枠を詰める(下から積むときは下の端を保つ)
        HistoryLayout.Rect rect = p.box();
        int unused = (count - rows.size()) * ROW;
        int rowsTop = p.rowsTop();
        int hintTop = p.hintTop();
        if (unused > 0) {
            if (p.upward()) {
                rect = new HistoryLayout.Rect(rect.x(), rect.y() + unused, rect.width(), rect.height() - unused);
                rowsTop += unused;
                hintTop += unused;
            } else {
                rect = new HistoryLayout.Rect(rect.x(), rect.y(), rect.width(), rect.height() - unused);
                if (hintTop > rowsTop) {
                    hintTop -= unused;
                }
            }
        }
        return new Box(rect, rowsTop, rows, first, hint, hintTop, p.upward());
    }

    /** 今の描画の一覧(広げるかは、前の形とマウスの位置で決める)。 */
    private Box currentBox(Screen screen, double mouseX, double mouseY) {
        Host host = host(screen);
        if (host == null || store == null) {
            return null;
        }
        EditBox field = host.field(screen);
        List<SearchHistoryStore.Entry> all = candidates(field);
        Box wide = box(screen, host, field, all, true);
        if (wide == null) {
            expanded = false;
            return null;
        }
        Box narrow = box(screen, host, field, all, false);
        boolean hover = narrow.contains(mouseX, mouseY) || (expanded && wide.contains(mouseX, mouseY));
        expanded = hover;
        Box box = hover ? wide : narrow;
        lastUpward = box.upward();
        scroll = box.first();
        return box;
    }

    private static List<String> hintLines() {
        return List.of(Component.translatable("gomacraft_utility.search_history.hint").getString().split("\n"));
    }

    private static String label(SearchHistoryStore.Entry entry) {
        return entry.favorite() ? FAVORITE_MARK + entry.text() : entry.text();
    }

    private void render(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        Box box = currentBox(screen, event.getMouseX(), event.getMouseY());
        shown = box;
        shownScreen = box == null ? null : screen;
        Host host = host(screen);
        shownField = box == null || host == null ? null : host.bounds(host.field(screen));
        if (box == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int hovered = box.indexAt(event.getMouseX(), event.getMouseY());
        HistoryLayout.Rect r = box.rect();
        g.pose().pushPose();
        // 画面のアイテムとツールチップの上に出す
        g.pose().translate(0, 0, Z);
        g.fill(r.x() - 1, r.y() - 1, r.right() + 1, r.bottom() + 1, 0xFF8B8B8B);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), 0xF0101010);
        int avail = r.width() - PAD * 2;
        for (int i = 0; i < box.hint().size(); i++) {
            g.drawString(font, ellipsize(font, box.hint().get(i), avail), r.x() + PAD, box.hintTop() + i * ROW + 2,
                    0xFF9A9A9A, false);
        }
        // 横に流すのは 1 行だけ: カーソルを合わせた行、無ければ ↑↓ で選んだ行
        int flowing = hovered >= 0 ? hovered : selected;
        boolean flowed = false;
        for (int i = 0; i < box.rows().size(); i++) {
            SearchHistoryStore.Entry entry = box.rows().get(i);
            int index = box.first() + i;
            int y = box.rowY(i);
            if (index == selected || index == hovered) {
                g.fill(r.x() + 1, y, r.right() - 1, y + ROW, 0x60FFFFFF);
            }
            String text = label(entry);
            int color = entry.favorite() ? 0xFFFFD866 : 0xFFE0E0E0;
            int overflow = font.width(text) - avail;
            if (overflow <= 0) {
                g.drawString(font, text, r.x() + PAD, y + 2, color, false);
            } else if (index == flowing) {
                // はみ出す語は横に流して全文を見せる(枠の中だけに描く)
                if (!text.equals(marqueeText)) {
                    marqueeText = text;
                    marqueeStart = Util.getMillis();
                }
                flowed = true;
                int offset = HistoryLayout.marqueeOffset(Util.getMillis() - marqueeStart, overflow);
                g.enableScissor(r.x() + PAD, y, r.right() - PAD, y + ROW);
                g.drawString(font, text, r.x() + PAD - offset, y + 2, color, false);
                g.disableScissor();
            } else {
                g.drawString(font, ellipsize(font, text, avail), r.x() + PAD, y + 2, color, false);
            }
        }
        if (!flowed) {
            // どの行も流れないコマでは、流し始めを戻す(同じ行へ戻ったときも頭から流す)
            marqueeText = null;
        }
        g.pose().popPose();
    }

    /** 幅に収まらない語を「…」で切る。 */
    private static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    /**
     * ウィンドウを出しているあいだ、ウィンドウか欄の上に出るツールチップを止める。描画の順で、ツールチップは画面の描画の中、
     * ウィンドウはその後に描くので、前の描画のウィンドウで決める(1 コマ遅れる)。
     */
    private void onTooltip(RenderTooltipEvent.Pre event) {
        Box box = shown;
        HistoryLayout.Rect fieldRect = shownField;
        if (box == null || shownScreen != Minecraft.getInstance().screen) {
            return;
        }
        int x = event.getX();
        int y = event.getY();
        if (box.contains(x, y) || (fieldRect != null && fieldRect.contains(x, y))) {
            event.setCanceled(true);
        }
    }

    /** キーを扱う。扱ったら true(画面と EMI へは渡さない)。 */
    public boolean key(Screen screen, int key) {
        EditBox field = field(screen);
        if (field == null || !field.isVisible() || !field.isFocused() || store == null) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            // 見た目の向きに合わせる: 下から積むときは、↑で欄から遠い(古い)方へ動く
            int size = candidates(field).size();
            if (size == 0) {
                return false;
            }
            if ((key == GLFW.GLFW_KEY_UP) == lastUpward) {
                selected = selected + 1 >= size ? 0 : selected + 1;
            } else {
                selected = selected <= 0 ? size - 1 : selected - 1;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            List<SearchHistoryStore.Entry> all = candidates(field);
            boolean used = false;
            if (selected >= 0 && selected < all.size()) {
                use(screen, field, all.get(selected).text());
                used = true;
            } else {
                // 画面が Enter で欄の focus を外す。その前に記す
                record(field.getValue());
                recorder.recorded();
            }
            selected = -1;
            scroll = 0;
            return used;
        }
        return false;
    }

    private void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        Box box = currentBox(event.getScreen(), event.getMouseX(), event.getMouseY());
        if (box == null || !box.contains(event.getMouseX(), event.getMouseY())) {
            return;
        }
        // 下から積むときは上が古い: ホイールを上へ回すと古いものへ遡る(上から積むときは逆)
        boolean older = (event.getScrollDeltaY() > 0) == box.upward();
        scroll += older ? 1 : -1;
        if (scroll < 0) {
            scroll = 0;
        }
        event.setCanceled(true);
    }

    /** 押したのを扱う。扱ったら true(画面と EMI へは渡さない)。 */
    public boolean click(Screen screen, double mouseX, double mouseY, int button) {
        // 前に押したものの離す操作が届かなかったとき(画面が変わった、など)に、関係の無い離す操作を飲まない
        swallowRelease = false;
        Box box = currentBox(screen, mouseX, mouseY);
        if (box == null) {
            return false;
        }
        EditBox field = field(screen);
        int index = box.indexAt(mouseX, mouseY);
        if (index < 0 && box.contains(mouseX, mouseY)) {
            // 一覧の余白・案内の行。下の画面へ通さない
            swallowRelease = true;
            return true;
        }
        if (index < 0) {
            if (!field.isMouseOver(mouseX, mouseY)) {
                // 欄の外を押した。今の語を記す(⚠ Nature's Compass は欄の外を押しても focus を外さないので、
                // focus の見張り(watchFocus)では拾えない。ウィンドウが出ていないときは見張りが拾う)
                record(field.getValue());
                recorder.recorded();
                selected = -1;
                scroll = 0;
            }
            return false;
        }
        String text = box.rows().get(index - box.first()).text();
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
            use(screen, field, text);
        }
        selected = -1;
        swallowRelease = true;
        return true;
    }

    /** 履歴の語で検索し、欄の focus を外す(結果をすぐ見られるように)。 */
    private void use(Screen screen, EditBox field, String text) {
        Host host = host(screen);
        if (host != null) {
            host.apply(screen, field, text);
        }
        record(text);
        recorder.recorded();
        field.setFocused(false);
        if (screen.getFocused() == field) {
            screen.setFocused(null);
        }
        scroll = 0;
    }

    private void onClose(Screen screen) {
        Host host = host(screen);
        EditBox field = host == null ? null : host.field(screen);
        boolean present = field != null && field.isVisible();
        String text = recorder.close(present, present && field.isFocused(), present ? field.getValue() : "",
                host != null && host.recordOnClose());
        if (text != null) {
            record(text);
        }
        selected = -1;
        scroll = 0;
        expanded = false;
        marqueeText = null;
        shown = null;
        shownScreen = null;
        shownField = null;
    }
}
