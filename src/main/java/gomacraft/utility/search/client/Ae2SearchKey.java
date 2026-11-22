package gomacraft.utility.search.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
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
 * AE2 の端末の検索のキー(goma-org/gomabase#2295)。
 *
 * <ul>
 *   <li>マウスの下の品の名前をキー(既定は F)で検索欄へ送る。Alt を押しながらなら {@code @<Mod の名前空間>}。
 *       送り先は、端末の品の欄の品なら <b>EMI の</b>検索欄(端末で見つけた品のレシピを EMI で引くため。EMI が無ければ
 *       AE2 の検索欄)、それ以外(持ち物・EMI の一覧)なら AE2 の検索欄。</li>
 *   <li>EMI の検索欄の語を、キー(既定は G)で AE2 の検索欄へ写す(AE2 と EMI の検索を同期していないときに使う)。</li>
 * </ul>
 *
 * <p>文字を打っている最中(AE2 の検索欄・ほかの入力欄・EMI の検索欄に focus がある)は何もしない。
 * 送ったときは、続けて届くそのキーの文字を捨てる(どこかの入力欄に入らないように)。
 */
public final class Ae2SearchKey {
    public static final KeyMapping KEY = new KeyMapping("key.gomacraft_utility.ae2_fill_search",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.gomacraft_utility");
    public static final KeyMapping COPY_EMI = new KeyMapping("key.gomacraft_utility.ae2_copy_emi_search",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.gomacraft_utility");

    private static Screen swallowCharFor;
    private static KeyMapping swallowKey;

    private Ae2SearchKey() {
    }

    public static void init(FeatureContext context) {
        context.modBus().addListener(RegisterKeyMappingsEvent.class,
                event -> Features.guard(SearchFeatures.AE2_SEARCH_KEY, () -> {
                    event.register(KEY);
                    event.register(COPY_EMI);
                }));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class,
                event -> Features.guard(SearchFeatures.AE2_SEARCH_KEY, () -> onKey(event)));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.CharacterTyped.Pre.class,
                event -> Features.guard(SearchFeatures.AE2_SEARCH_KEY, () -> onChar(event)));
    }

    /** 割り当てのキーそのものの一致(⚠ isActiveAndMatches は修飾キー「なし」まで見るので、Alt を押すと偽になる)。 */
    private static boolean pressed(KeyMapping mapping, ScreenEvent.KeyPressed.Pre event) {
        return !mapping.isUnbound()
                && mapping.getKey().equals(InputConstants.getKey(event.getKeyCode(), event.getScanCode()));
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre event) {
        swallowCharFor = null;
        swallowKey = null;
        if (!(event.getScreen() instanceof MEStorageScreen<?> screen)) {
            return;
        }
        boolean fill = pressed(KEY, event);
        boolean copy = !fill && pressed(COPY_EMI, event);
        if ((!fill && !copy) || typing(screen)) {
            return;
        }
        EditBox field = ((MEStorageScreenKeyAccessor) screen).gomacraftUtility$keySearchField();
        String text;
        boolean sent;
        if (fill) {
            boolean modId = (event.getModifiers() & GLFW.GLFW_MOD_ALT) != 0;
            text = Ae2Hover.hoveredSearchText(screen, modId);
            if (text == null || text.isEmpty()) {
                return;
            }
            if (screen.getSlotUnderMouse() instanceof RepoSlot && emi()) {
                EmiBridge.setSearchText(text);
                sent = true;
            } else {
                // 入れる先が無い(検索欄が隠れていて EMI も無い)なら、キーを止めず、履歴にも記さない
                sent = Ae2Search.apply(field, text);
            }
        } else {
            if (!emi()) {
                return;
            }
            text = EmiBridge.getSearchText();
            if (text == null || text.isBlank() || field == null || !field.isVisible()) {
                return;
            }
            field.setValue(text);
            field.moveCursorToEnd(false);
            sent = true;
        }
        if (!sent) {
            return;
        }
        Ae2SearchHistory.recordFromOtherFeature(text);
        swallowCharFor = screen;
        swallowKey = fill ? KEY : COPY_EMI;
        event.setCanceled(true);
    }

    private static boolean emi() {
        return ModList.get().isLoaded("emi");
    }

    private static void onChar(ScreenEvent.CharacterTyped.Pre event) {
        Screen screen = swallowCharFor;
        KeyMapping key = swallowKey;
        swallowCharFor = null;
        swallowKey = null;
        if (screen != null && key != null && screen == event.getScreen()
                && Character.toLowerCase(event.getCodePoint()) == Character.toLowerCase(keyChar(key))) {
            event.setCanceled(true);
        }
    }

    /** 割り当てたキーの文字(F なら 'f')。文字の無いキーなら 0。 */
    private static char keyChar(KeyMapping key) {
        String name = key.getKey().getDisplayName().getString();
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
        return emi() && EmiBridge.isSearchFocused();
    }

    static double[] mouse() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double x = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        return new double[] {x, y};
    }
}
