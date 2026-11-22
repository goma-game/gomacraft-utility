package gomacraft.utility.emiae.client;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.CraftingTermMenu;
import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStackInteraction;
import gomacraft.utility.Features;
import gomacraft.utility.emiae.EmiAeFeatures;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * EMI の一覧の品を、AE の端末を開いたまま 1 回の操作で手元へ(既定は Alt + 左クリック。操作の設定で変えられ、
 * キーボードのキーに割り当て直してもよい —— そのときは品の上でそのキーを押す)。
 *
 * <ul>
 *   <li>ネットワークに品(アイテム)が在れば、1 スタックを手持ちへ移す(AE2 の端末の Shift + クリックと同じ動き)。
 *       液体などは手持ちへ移せないので、作れるなら自動クラフトへ、作れなければ案内を出す。</li>
 *   <li>無くて作れるなら、AE2 の自動クラフトを依頼する。数の画面を飛ばして 1 個(Shift も押せば 1 スタック)を
 *       「すぐ始める」で確かめる。材料が足りなければ、AE2 のクラフト計画の画面が残る(そこで直せる)。</li>
 * </ul>
 *
 * <p>通信は AE2 自身のもの({@code MEInteractionPacket}・{@code ConfirmAutoCraftPacket})だけを使う。サーバの
 * 側の確かめ(端末につながっているか・電力)は AE2 がする。⚠ EMI の手持ちには何も入れない(goma-org/gomabase#2286)。
 */
public final class QuickCraft {
    public static final KeyMapping KEY = new KeyMapping("key.gomacraft_utility.ae_quick_craft",
            KeyConflictContext.GUI, KeyModifier.ALT, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT,
            "key.categories.gomacraft_utility");

    /** 依頼してから数の画面が開くまで待つ時間。 */
    private static final long PENDING_MS = 3000;

    /**
     * 依頼の待ち。{@code screen} は、依頼の後に最初に見た数の画面(その画面の品が違えば、手で開いた別の画面なので諦める。
     * 依頼の後に開いた画面だけを押す)。
     */
    private record Pending(AEKey key, int amount, long deadline, CraftAmountScreen screen) {
        Pending seen(CraftAmountScreen s) {
            return new Pending(key, amount, deadline, s);
        }
    }

    private static Pending pending;

    private QuickCraft() {
    }

    /** 機能の初期化(画面の側だけ)。 */
    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class,
                event -> Features.guard(EmiAeFeatures.QUICK_CRAFT, () -> event.register(KEY)));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> Features.guard(EmiAeFeatures.QUICK_CRAFT, QuickCraft::tick));
    }

    /** EMI がクリックを扱う前に呼ばれる(mixin)。この操作で扱ったら true(EMI には渡さない)。 */
    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!KEY.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(button))) {
            return false;
        }
        return handle(EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false));
    }

    /** EMI がキーを扱う前に呼ばれる(mixin)。キーボードに割り当て直したとき用。扱ったら true。 */
    public static boolean onKeyPressed(int keyCode, int scanCode) {
        if (!KEY.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            return false;
        }
        return handle(EmiApi.getHoveredStack(false));
    }

    private static boolean handle(EmiStackInteraction hovered) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof MEStorageMenu menu)) {
            return false;
        }
        if (hovered == null || hovered.isEmpty()) {
            return false;
        }
        AEKey key = StockBadges.keyOf(hovered.getStack());
        IClientRepo repo = menu.getClientRepo();
        if (key == null || repo == null) {
            return false;
        }
        GridInventoryEntry entry = find(repo, key);
        boolean stored = entry != null && entry.getStoredAmount() > 0;
        boolean craftable = entry != null && entry.isCraftable();
        // AE2 の Shift + クリックはアイテムにしか効かない(液体などは黙って飲まれる)
        if (stored && key instanceof AEItemKey) {
            menu.handleInteraction(entry.getSerial(), InventoryAction.SHIFT_CLICK);
            return true;
        }
        if (craftable) {
            int amount = Screen.hasShiftDown() ? stackSize(key) : unit(key);
            pending = new Pending(key, amount, System.currentTimeMillis() + PENDING_MS, null);
            menu.handleInteraction(entry.getSerial(), InventoryAction.AUTO_CRAFT);
            return true;
        }
        // パターンは無いが、作業台のレシピで材料が揃うなら、クラフト端末の枠で作る(GridCraft)
        if (!stored && menu instanceof CraftingTermMenu craftingMenu
                && Features.guard(EmiAeFeatures.GRID_CRAFT, () -> GridCraft.tryStart(craftingMenu, hovered.getStack()),
                        false)) {
            return true;
        }
        String message = stored
                ? "gomacraft_utility.ae_quick_craft.not_an_item"
                : "gomacraft_utility.ae_quick_craft.unavailable";
        mc.player.displayClientMessage(Component.translatable(message, key.getDisplayName()), true);
        return true;
    }

    /** 依頼した品の数の画面が開いたら、数を入れて「すぐ始める」で確かめる。 */
    private static void tick() {
        Pending p = pending;
        if (p == null) {
            return;
        }
        if (System.currentTimeMillis() > p.deadline()) {
            pending = null;
            return;
        }
        if (!(Minecraft.getInstance().screen instanceof CraftAmountScreen screen)) {
            return;
        }
        if (p.screen() == null) {
            p = p.seen(screen);
            pending = p;
        } else if (p.screen() != screen) {
            // 依頼の後に見た画面とは別の数の画面(閉じて手で開き直した): 押さない
            pending = null;
            return;
        }
        GenericStack what = screen.getMenu().getWhatToCraft();
        // 数の画面の中身はサーバから少し遅れて届く。届くまで待ち、品が違えば諦める
        if (what == null) {
            return;
        }
        pending = null;
        if (p.key().equals(what.what())) {
            screen.getMenu().confirm(p.amount(), false, true);
        }
    }

    private static GridInventoryEntry find(IClientRepo repo, AEKey key) {
        for (GridInventoryEntry entry : repo.getAllEntries()) {
            if (key.equals(entry.getWhat())) {
                return entry;
            }
        }
        return null;
    }

    /** 1 個(液体などは 1 単位 = バケツ 1 杯。AE2 の数の画面の初めの値と同じ)。 */
    private static int unit(AEKey key) {
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, key.getAmountPerUnit()));
    }

    /** 1 スタック(液体などは 64 単位)。 */
    private static int stackSize(AEKey key) {
        if (key instanceof AEItemKey item) {
            return Math.max(1, item.getMaxStackSize());
        }
        return (int) Math.min(Integer.MAX_VALUE, 64L * unit(key));
    }
}
