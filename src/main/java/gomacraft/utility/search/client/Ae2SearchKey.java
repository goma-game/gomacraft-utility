package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import com.mojang.blaze3d.platform.InputConstants;
import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.mixin.ae2_search_key.MEStorageScreenKeyAccessor;
import gomacraft.utility.search.SearchFeatures;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * AE2 の端末で、マウスの下の品(端末の一覧・持ち物・EMI の一覧)の名前を、キー(既定は F)で検索欄へ送る。
 * Alt を押しながらなら {@code @<Mod の名前空間>} を送る(AE2 の Mod の検索)。
 *
 * <p>文字を打っている最中(AE2 の検索欄・ほかの入力欄・EMI の検索欄に focus がある)は何もしない。
 * 送ったときは、続けて届く文字の {@code f} を捨てる(どこかの入力欄に入らないように)。
 */
public final class Ae2SearchKey {
    public static final KeyMapping KEY = new KeyMapping("key.gomacraft_utility.ae2_fill_search",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.gomacraft_utility");

    private static Screen swallowCharFor;

    private Ae2SearchKey() {
    }

    public static void init(FeatureContext context) {
        context.modBus().addListener(RegisterKeyMappingsEvent.class,
                event -> Features.guard(SearchFeatures.AE2_SEARCH_KEY, () -> event.register(KEY)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class,
                event -> Features.guard(SearchFeatures.AE2_SEARCH_KEY, () -> onKey(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.CharacterTyped.Pre.class,
                event -> Features.guard(SearchFeatures.AE2_SEARCH_KEY, () -> onChar(event)));
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre event) {
        swallowCharFor = null;
        if (!(event.getScreen() instanceof MEStorageScreen<?> screen)) {
            return;
        }
        // ⚠ isActiveAndMatches は割り当ての修飾キー(既定は「なし」)まで見るので、Alt を押すと偽になる。
        // キーそのものの一致だけを見て、Alt は下で分ける
        if (KEY.isUnbound() || !KEY.getKey().equals(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            return;
        }
        if (typing(screen)) {
            return;
        }
        boolean modId = (event.getModifiers() & GLFW.GLFW_MOD_ALT) != 0;
        String text = Ae2Hover.hoveredSearchText(screen, modId);
        if (text == null || text.isEmpty()) {
            return;
        }
        // 入れる先が無い(検索欄が隠れていて EMI も無い)なら、キーを止めず、履歴にも記さない
        if (!Ae2Search.apply(((MEStorageScreenKeyAccessor) screen).gomacraftUtility$keySearchField(), text)) {
            return;
        }
        Ae2SearchHistory.recordFromOtherFeature(text);
        swallowCharFor = screen;
        event.setCanceled(true);
    }

    private static void onChar(ScreenEvent.CharacterTyped.Pre event) {
        Screen screen = swallowCharFor;
        swallowCharFor = null;
        if (screen != null && screen == event.getScreen()
                && Character.toLowerCase(event.getCodePoint()) == Character.toLowerCase(keyChar())) {
            event.setCanceled(true);
        }
    }

    /** 割り当てたキーの文字(F なら 'f')。文字の無いキーなら 0。 */
    private static char keyChar() {
        String name = KEY.getKey().getDisplayName().getString();
        return name.length() == 1 ? name.charAt(0) : 0;
    }

    private static boolean typing(Screen screen) {
        if (screen.getFocused() instanceof EditBox box && box.isFocused()) {
            return true;
        }
        MEStorageScreenKeyAccessor accessor = (MEStorageScreenKeyAccessor) screen;
        EditBox field = accessor.gomacraftUtility$keySearchField();
        if (field != null && field.isFocused()) {
            return true;
        }
        return ModList.get().isLoaded("emi") && EmiBridge.isSearchFocused();
    }

    static double[] mouse() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double x = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        return new double[] {x, y};
    }
}
