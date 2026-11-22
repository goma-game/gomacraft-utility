package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.mixin.ae2_search_history.MEStorageScreenHistoryAccessor;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.SearchHistoryStore;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * AE2 の端末の検索語の履歴(goma-org/gomabase#2295。作りの参考は AE2 Recent Search —— コードは写していない)。
 *
 * <ul>
 *   <li>検索欄に focus があるあいだ、欄の下に履歴を出す(お気に入り → 新しい順、{@link #VISIBLE} 件まで)。</li>
 *   <li>記すのは、Enter・欄の外を押したとき・端末を閉じたとき・F キーで送ったとき(打つ 1 字ごとには記さない)。</li>
 *   <li>左で使う、右で消す、Shift + 左か中でお気に入りを付け外し。↑↓で選び、Enter で使う。</li>
 *   <li>履歴は手元の {@code config/gomacraft_utility/search_history.json} に、プレイヤーごとに置く(サーバには送らない)。</li>
 * </ul>
 */
public final class Ae2SearchHistory {
    static final int VISIBLE = 10;
    private static final int ROW = 11;
    private static final int PAD = 2;
    private static final int MAX_WIDTH = 180;
    private static final String FAVORITE_MARK = "★ ";

    private static SearchHistoryStore store;
    private static Path file;
    /** ↑↓で選んだ行(-1 は選んでいない)。 */
    private static int selected = -1;

    private Ae2SearchHistory() {
    }

    public static void init(FeatureContext context) throws Exception {
        file = FMLPaths.CONFIGDIR.get().resolve(GomacraftUtility.MOD_ID).resolve("search_history.json");
        try {
            store = SearchHistoryStore.load(file);
        } catch (Exception e) {
            // 読めないファイルは上書きしない(人が直せるように残す)。この機能だけを止める
            throw new IllegalStateException("search history unreadable: " + FeatureRegistry.describe(e), e);
        }
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, event -> guard(() -> render(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class, event -> guard(() -> onKey(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.CharacterTyped.Post.class, event -> guard(() -> selected = -1));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Pre.class, event -> guard(() -> onClick(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Closing.class, event -> guard(() -> onClose(event.getScreen())));
    }

    private static void guard(Runnable body) {
        Features.guard(SearchFeatures.AE2_SEARCH_HISTORY, body);
    }

    /** ほかの機能(F キー)が検索語を入れたときに記す。履歴の機能が切れていれば何もしない。 */
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
        if (store != null && store.record(player(), text)) {
            save();
        }
    }

    private static void save() {
        try {
            store.save(file);
        } catch (Exception e) {
            throw new IllegalStateException("search history unwritable: " + FeatureRegistry.describe(e), e);
        }
    }

    private static List<SearchHistoryStore.Entry> visible() {
        List<SearchHistoryStore.Entry> all = store.list(player());
        return all.size() > VISIBLE ? all.subList(0, VISIBLE) : all;
    }

    /** 一覧を出す位置と大きさ(出さないなら null)。 */
    private record Box(int x, int y, int width, List<SearchHistoryStore.Entry> rows) {
        int height() {
            return rows.size() * ROW + PAD * 2;
        }

        int rowAt(double mouseX, double mouseY) {
            if (mouseX < x || mouseX >= x + width || mouseY < y + PAD || mouseY >= y + PAD + rows.size() * ROW) {
                return -1;
            }
            return (int) ((mouseY - y - PAD) / ROW);
        }
    }

    private static Box box(EditBox field) {
        if (field == null || !field.isVisible() || !field.isFocused()) {
            return null;
        }
        List<SearchHistoryStore.Entry> rows = visible();
        if (rows.isEmpty()) {
            return null;
        }
        Font font = Minecraft.getInstance().font;
        int width = field.getWidth();
        for (SearchHistoryStore.Entry entry : rows) {
            width = Math.max(width, font.width(label(entry)) + PAD * 2 + 2);
        }
        width = Math.min(width, Math.max(field.getWidth(), MAX_WIDTH));
        return new Box(field.getX(), field.getY() + field.getHeight() + 1, width, rows);
    }

    private static String label(SearchHistoryStore.Entry entry) {
        return entry.favorite() ? FAVORITE_MARK + entry.text() : entry.text();
    }

    private static void render(ScreenEvent.Render.Post event) {
        Box box = box(field(event.getScreen()));
        if (box == null) {
            return;
        }
        if (selected >= box.rows().size()) {
            selected = -1;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int hovered = box.rowAt(event.getMouseX(), event.getMouseY());
        g.pose().pushPose();
        // 端末の品の上に出す
        g.pose().translate(0, 0, 400);
        g.fill(box.x() - 1, box.y() - 1, box.x() + box.width() + 1, box.y() + box.height() + 1, 0xFF8B8B8B);
        g.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), 0xF0101010);
        for (int i = 0; i < box.rows().size(); i++) {
            SearchHistoryStore.Entry entry = box.rows().get(i);
            int top = box.y() + PAD + i * ROW;
            if (i == selected || i == hovered) {
                g.fill(box.x() + 1, top, box.x() + box.width() - 1, top + ROW, 0x60FFFFFF);
            }
            String text = font.plainSubstrByWidth(label(entry), box.width() - PAD * 2);
            g.drawString(font, text, box.x() + PAD, top + 2, entry.favorite() ? 0xFFFFD866 : 0xFFE0E0E0, false);
        }
        g.pose().popPose();
        if (hovered >= 0) {
            g.renderTooltip(font, Component.translatable("gomacraft_utility.search_history.hint"),
                    event.getMouseX(), event.getMouseY());
        }
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre event) {
        EditBox field = field(event.getScreen());
        if (field == null || !field.isVisible() || !field.isFocused()) {
            return;
        }
        int key = event.getKeyCode();
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            int size = visible().size();
            if (size == 0) {
                return;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                selected = selected + 1 >= size ? 0 : selected + 1;
            } else {
                selected = selected <= 0 ? size - 1 : selected - 1;
            }
            event.setCanceled(true);
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            List<SearchHistoryStore.Entry> rows = visible();
            if (selected >= 0 && selected < rows.size()) {
                use(event.getScreen(), field, rows.get(selected).text());
                event.setCanceled(true);
            } else {
                // AE2 が Enter で欄の focus を外す。その前に記す
                record(field.getValue());
            }
            selected = -1;
        }
    }

    private static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        EditBox field = field(event.getScreen());
        Box box = box(field);
        if (box == null) {
            return;
        }
        int row = box.rowAt(event.getMouseX(), event.getMouseY());
        if (row < 0) {
            if (!field.isMouseOver(event.getMouseX(), event.getMouseY())) {
                // 欄の外を押した(focus が外れる)。今の語を記す
                record(field.getValue());
                selected = -1;
            }
            return;
        }
        String text = box.rows().get(row).text();
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

    /** 履歴の語で検索し、欄の focus を外す(結果が一覧に隠れないように)。 */
    private static void use(Screen screen, EditBox field, String text) {
        Ae2Search.apply(field, text);
        record(text);
        field.setFocused(false);
        screen.setFocused(null);
    }

    private static void onClose(Screen screen) {
        EditBox field = field(screen);
        if (field != null && field.isVisible()) {
            record(field.getValue());
        }
        selected = -1;
    }
}
