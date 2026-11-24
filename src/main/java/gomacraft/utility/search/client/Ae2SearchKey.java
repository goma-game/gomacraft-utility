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
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * AE2 の端末の検索のキー(goma-org/gomabase#2295)。
 *
 * <ul>
 *   <li>マウスの下の品の名前をキー(既定は F)で検索欄へ送る。Alt を押しながらなら {@code @<Mod の名前空間>}。
 *       送り先は、端末の品の欄の品なら <b>EMI の</b>検索欄(端末で見つけた品のレシピを EMI で引くため。EMI が無ければ
 *       AE2 の検索欄)、それ以外(持ち物・EMI の一覧)なら AE2 の検索欄。</li>
 *   <li>F を <b>EMI の検索欄の上</b>で押したら、EMI の検索語を AE2 の検索欄へ写す(AE2 と EMI の検索を同期していないときに
 *       使う —— ユーザの指定、goma-org/gomabase#2295)。同じことをする専用のキーもある(既定は割り当てなし)。</li>
 *   <li>逆に、F を <b>AE2 の検索欄の上</b>で押したら(欄に focus が無いとき)、AE2 の検索語を EMI の検索欄へ入れる
 *       (4 回目の確かめの 2)。欄が空なら何もしない。⚠ 同じ機能に置く: 同じ F のキー・同じ「打っている最中は何もしない」と
 *       「続く文字を捨てる」の決まりを使い、EMI の検索欄の上の F と対になる操作なので、別の機能にすると片方だけ切れて
 *       F の意味が欄によって変わる。</li>
 * </ul>
 *
 * <p>文字を打っている最中(AE2 の検索欄・ほかの入力欄・EMI の検索欄に focus がある)は何もしない。
 * 送ったときは、続けて届くそのキーの文字を捨てる(どこかの入力欄に入らないように)。
 */
public final class Ae2SearchKey {
    public static final KeyMapping KEY = new KeyMapping("key.gomacraft_utility.ae2_fill_search",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.gomacraft_utility");
    /** 既定は割り当てなし(F を EMI の検索欄の上で押せば写せる。別のキーで写したい人が割り当てる)。 */
    public static final KeyMapping COPY_EMI = new KeyMapping("key.gomacraft_utility.ae2_copy_emi_search",
            KeyConflictContext.GUI, InputConstants.UNKNOWN, "key.categories.gomacraft_utility");

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

    /**
     * 割り当てのキーの一致。⚠ isActiveAndMatches は修飾キー「なし」の割り当てで修飾キーを押すと偽になる(Alt + F が
     * 届かない)ので、キーそのものを比べる。割り当てに修飾キー(Ctrl など)が付いていれば、それが押されているときだけ。
     */
    private static boolean pressed(KeyMapping mapping, ScreenEvent.KeyPressed.Pre event) {
        if (mapping.isUnbound()
                || !mapping.getKey().equals(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            return false;
        }
        KeyModifier modifier = mapping.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(KeyConflictContext.GUI);
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
        if (fill && emi()) {
            // F を EMI の検索欄の上で押したら、EMI の検索語を AE2 の検索欄へ写す(ユーザの指定 —— #2295)
            double[] mouse = mouse();
            if (EmiBridge.isMouseOverSearch(mouse[0], mouse[1])) {
                fill = false;
                copy = true;
            }
        }
        if (fill && emi() && field != null && field.isVisible()) {
            // F を AE2 の検索欄の上で押したら(打っていないとき)、欄の語を EMI の検索欄へ入れる(EMI の検索欄の上の F の
            // 逆 —— 4 回目の確かめの 2)。欄が空なら何もしない(キーも止めない)
            double[] mouse = mouse();
            if (field.isMouseOver(mouse[0], mouse[1])) {
                String value = field.getValue();
                if (value == null || value.isBlank()) {
                    return;
                }
                EmiBridge.setSearchText(value);
                Ae2SearchHistory.recordFromOtherFeature(value);
                swallowCharFor = screen;
                swallowKey = KEY;
                event.setCanceled(true);
                return;
            }
        }
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
        // 捨てる文字は、押したキーの文字(EMI の検索欄の上の F で写したときも F)
        swallowKey = pressed(KEY, event) ? KEY : COPY_EMI;
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
