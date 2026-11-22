package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.core.AEConfig;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.ae2_search_sync_toggle.MEStorageScreenSyncAccessor;
import gomacraft.utility.search.SearchFeatures;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * AE2 の端末の検索欄をダブルクリックすると、AE2 の「EMI の検索と同期する」設定を、その場だけ反転する
 * (同期なら非同期、非同期なら同期)。もう一度ダブルクリックするまで、端末を閉じても保つ(ゲームを閉じれば戻る。
 * AE2 の設定のファイルは変えない)。反転したら、検索欄の左に今の同期の状態を少しの間だけ出す。
 */
public final class Ae2SearchSyncToggle {
    /** ダブルクリックとみなす 2 回のクリックの間(ミリ秒)。 */
    private static final long DOUBLE_CLICK_MS = 300;

    private static final long NOTICE_MS = 1500;

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
            notice = Component.translatable(synced
                    ? "gomacraft_utility.search_sync.on" : "gomacraft_utility.search_sync.off");
            noticeUntil = now + NOTICE_MS;
            return;
        }
        lastClick = now;
    }

    /** 反転した直後の知らせを、検索欄の左に少しの間だけ出す(画面の下の知らせは端末の画面の裏に隠れる)。 */
    private static void onRender(ScreenEvent.Render.Post event) {
        Component text = notice;
        if (text == null || Util.getMillis() > noticeUntil
                || !(event.getScreen() instanceof MEStorageScreen<?> screen)) {
            return;
        }
        EditBox field = ((MEStorageScreenSyncAccessor) screen).gomacraftUtility$syncSearchField();
        if (field == null || !field.isVisible()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int width = font.width(text);
        int x = Math.max(2, field.getX() - width - 6);
        int y = field.getY() + (field.getHeight() - 8) / 2;
        var g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(x - 2, y - 2, x + width + 2, y + 10, 0xE0101010);
        g.drawString(font, text, x, y, 0xFFFFFF55, false);
        g.pose().popPose();
    }
}
