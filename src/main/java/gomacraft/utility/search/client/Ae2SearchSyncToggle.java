package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.widgets.AETextField;
import appeng.core.AEConfig;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.ae2_search_sync_toggle.MEStorageScreenSyncAccessor;
import gomacraft.utility.search.SearchFeatures;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * AE2 の端末の検索欄をダブルクリックすると、AE2 の「EMI の検索と同期する」設定を、その場だけ反転する
 * (同期なら非同期、非同期なら同期)。もう一度ダブルクリックするまで、端末を閉じても保つ(ゲームを閉じれば戻る。
 * AE2 の設定のファイルは変えない)。反転して EMI との同期が入になったときだけ、検索欄の下に「EMI と同期」と少しの間だけ出す(切になったときは出さない)。
 *
 * <p>反転しているあいだは、検索欄を青で縁取る(goma-org/gomabase#2295 の 2 回目の確かめの 3)。縁取るのは反転している
 * ときだけで、AE2 の設定のとおりのときは縁取らない(いつもの見た目のまま)。色は 1 つにする(色で同期の入り切りを分けると、
 * どちらの色が何かを覚える要が出る。入り切りは反転した直後の知らせが言う)。黄は EMI の検索欄の focus の色なので避ける。
 */
public final class Ae2SearchSyncToggle {
    /** ダブルクリックとみなす 2 回のクリックの間(ミリ秒)。 */
    private static final long DOUBLE_CLICK_MS = 300;

    private static final long NOTICE_MS = 1500;
    /** 反転しているあいだの縁の色(青)。 */
    private static final int OUTLINE = 0xFF3C8CFF;

    private static volatile boolean inverted;
    private static long lastClick;
    private static Component notice;
    private static long noticeUntil;

    private Ae2SearchSyncToggle() {
    }

    public static void init(FeatureContext context) {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Pre.class,
                event -> Features.guard(SearchFeatures.AE2_SEARCH_SYNC_TOGGLE, () -> onClick(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class,
                event -> Features.guard(SearchFeatures.AE2_SEARCH_SYNC_TOGGLE, () -> onRender(event)));
    }

    /** AE2 の同期の設定を反転しているか(mixin が読む)。 */
    public static boolean inverted() {
        return inverted;
    }

    private static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !(event.getScreen() instanceof MEStorageScreen<?> screen)) {
            return;
        }
        EditBox field = ((MEStorageScreenSyncAccessor) screen).gomacraftUtility$syncSearchField();
        if (field == null || !field.isVisible() || !field.isMouseOver(event.getMouseX(), event.getMouseY())) {
            lastClick = 0;
            return;
        }
        long now = Util.getMillis();
        if (lastClick != 0 && now - lastClick <= DOUBLE_CLICK_MS) {
            lastClick = 0;
            inverted = !inverted;
            // 端末が今使う値(設定の値を、反転の印で読み替えたもの)
            boolean synced = AEConfig.instance().isSyncWithExternalSearch() != inverted;
            // 同期が入になったときだけ「EMI と同期」と知らせる。切になったときは何も出さない(反転か設定どおりかは
            // 問わない —— 5 回目の確かめのユーザの指定)
            notice = synced ? Component.translatable("gomacraft_utility.search_sync.on") : null;
            noticeUntil = now + NOTICE_MS;
            return;
        }
        lastClick = now;
    }

    /**
     * 反転しているあいだは検索欄を縁取り、反転した直後の知らせを検索欄の下に少しの間だけ出す(画面の下の知らせは端末の
     * 画面の裏に隠れる)。
     */
    private static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MEStorageScreen<?> screen)) {
            return;
        }
        AETextField field = ((MEStorageScreenSyncAccessor) screen).gomacraftUtility$syncSearchField();
        if (field == null || !field.isVisible()) {
            return;
        }
        var g = event.getGuiGraphics();
        if (inverted) {
            // 欄の見た目の枠(字の位置より 2 ずつ外)の 1 つ外
            Rect2i area = field.getTooltipArea();
            g.renderOutline(area.getX() - 1, area.getY() - 1, area.getWidth() + 2, area.getHeight() + 2, OUTLINE);
        }
        Component text = notice;
        if (text == null || Util.getMillis() > noticeUntil) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int width = font.width(text);
        // 欄の下に出す(欄の左は検索語の履歴のウィンドウの場所)。右の端は欄の右に揃え、画面からはみ出さない
        Rect2i area = field.getTooltipArea();
        int x = Math.max(2, Math.min(area.getX() + area.getWidth() - width, screen.width - width - 2));
        int y = area.getY() + area.getHeight() + 3;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(x - 2, y - 2, x + width + 2, y + 10, 0xE0101010);
        g.drawString(font, text, x, y, 0xFF8CC0FF, false);
        g.pose().popPose();
    }
}
