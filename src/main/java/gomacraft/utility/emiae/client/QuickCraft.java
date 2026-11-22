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
import gomacraft.utility.settings.ClientSettings;
import gomacraft.utility.settings.Settings;
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
 * EMI の一覧の品を、AE の端末を開いたまま 1 回の操作で手元へ・作る(ユーザ決定 goma-org/gomabase#2295 の 11)。
 * 操作は操作の設定で変えられ、キーボードのキーに割り当て直してもよい(そのときは品の上でそのキーを押す)。
 *
 * <ul>
 *   <li><b>中クリック</b>({@link #AMOUNT_KEY}。AE2 の端末と同じ): 作れる品なら、AE2 のクラフトの数の画面を開く
 *       (数を決めて確定すると計画の画面へ)。</li>
 *   <li><b>Alt + クリック</b>({@link #KEY}): アイテムの在庫があれば 1 スタックを手持ちへ。無くて作れるなら、1 回分の数で
 *       計画の画面を開く(数の画面を飛ばす)。</li>
 *   <li><b>Alt + Shift + クリック</b>: 作れるなら 1 スタック分の数で計画の画面を開く。作れなくて在庫があれば取り出す。</li>
 *   <li>設定 {@link #SKIP_CONFIRM}(既定は切)を入にしたときだけ、計画の画面も飛ばして「すぐ始める」で作る
 *       (材料が足りなければ計画の画面が残る)。</li>
 *   <li>パターンが無く、作業台のレシピで材料が揃う品は {@link GridCraft}(計画の画面が無いので今の形)。</li>
 * </ul>
 *
 * <p>通信は AE2 自身のもの({@code MEInteractionPacket}・{@code ConfirmAutoCraftPacket})だけを使う。サーバの
 * 側の確かめ(端末につながっているか・電力)は AE2 がする。⚠ EMI の手持ちには何も入れない(goma-org/gomabase#2286)。
 * ⚠ EMI の既定の操作で中クリックを使うのは Ctrl + 中クリック(チートの 1 個をカーソルへ)だけで、素の中クリックとは
 * ぶつからない(EMI 1.1.24 の {@code EmiConfig})。
 */
public final class QuickCraft {
    public static final KeyMapping KEY = new KeyMapping("key.gomacraft_utility.ae_quick_craft",
            KeyConflictContext.GUI, KeyModifier.ALT, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT,
            "key.categories.gomacraft_utility");
    /** 数を決めて作る(AE2 の端末の中クリックと同じ)。 */
    public static final KeyMapping AMOUNT_KEY = new KeyMapping("key.gomacraft_utility.ae_craft_amount",
            KeyConflictContext.GUI, KeyModifier.NONE, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.gomacraft_utility");

    /** Alt + クリックで作るとき、計画の画面も飛ばして一気に作るか(既定は切 —— 量をその場で変えられないため)。 */
    public static final ClientSettings.Bool SKIP_CONFIRM =
            new ClientSettings.Bool(EmiAeFeatures.QUICK_CRAFT, "skip_confirm", false);

    /** 依頼してから数の画面が開くまで待つ時間。 */
    private static final long PENDING_MS = 3000;

    /**
     * 依頼の待ち。{@code screen} は、依頼の後に最初に見た数の画面(その画面の品が違えば、手で開いた別の画面なので諦める。
     * 依頼の後に開いた画面だけを押す)。{@code autoStart} が偽なら、計画の画面で人が確定する。
     */
    private record Pending(AEKey key, int amount, boolean autoStart, long deadline, CraftAmountScreen screen) {
        Pending seen(CraftAmountScreen s) {
            return new Pending(key, amount, autoStart, deadline, s);
        }
    }

    private enum Gesture {
        /** 中クリック: 数の画面を開くだけ。 */
        AMOUNT,
        /** Alt + クリック(Shift の有無は押したときに見る)。 */
        QUICK
    }

    private static Pending pending;

    private QuickCraft() {
    }

    /** 機能の初期化(画面の側だけ)。 */
    public static void init(IEventBus modBus) {
        Settings.client().register(SKIP_CONFIRM);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> Features.guard(EmiAeFeatures.QUICK_CRAFT, () -> {
            event.register(KEY);
            event.register(AMOUNT_KEY);
        }));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> Features.guard(EmiAeFeatures.QUICK_CRAFT, QuickCraft::tick));
    }

    /** EMI がクリックを扱う前に呼ばれる(mixin)。この操作で扱ったら true(EMI には渡さない)。 */
    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        Gesture gesture = gestureOf(InputConstants.Type.MOUSE.getOrCreate(button));
        return gesture != null && handle(EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false), gesture);
    }

    /** EMI がキーを扱う前に呼ばれる(mixin)。キーボードに割り当て直したとき用。扱ったら true。 */
    public static boolean onKeyPressed(int keyCode, int scanCode) {
        Gesture gesture = gestureOf(InputConstants.getKey(keyCode, scanCode));
        return gesture != null && handle(EmiApi.getHoveredStack(false), gesture);
    }

    /** 押されたものがどの操作か(どれでもなければ null)。修飾キーの付く Alt + クリックを先に見る。 */
    private static Gesture gestureOf(InputConstants.Key key) {
        if (KEY.isActiveAndMatches(key)) {
            return Gesture.QUICK;
        }
        // 修飾キー無しの割り当ては、Ctrl などを押している間は当たらない(Ctrl + 中クリックは EMI のチートに残る)
        if (AMOUNT_KEY.isActiveAndMatches(key)) {
            return Gesture.AMOUNT;
        }
        return null;
    }

    private static boolean handle(EmiStackInteraction hovered, Gesture gesture) {
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

        // 前の Alt + クリックの待ち(数の画面へ数を入れて確定する)を捨てる。残すと、この操作で開いた数の画面へ
        // 前の数が入って確定してしまう
        pending = null;
        if (gesture == Gesture.AMOUNT) {
            if (craftable) {
                // AE2 の端末の中クリックと同じ: 数の画面 → 確定で計画の画面
                menu.handleInteraction(entry.getSerial(), InventoryAction.AUTO_CRAFT);
            } else {
                say(mc, "gomacraft_utility.ae_quick_craft.no_pattern", key);
            }
            return true;
        }

        boolean bulk = Screen.hasShiftDown();
        // AE2 の Shift + クリックはアイテムにしか効かない(液体などは黙って飲まれる)
        boolean canTake = stored && key instanceof AEItemKey;
        if (canTake && (!bulk || !craftable)) {
            menu.handleInteraction(entry.getSerial(), InventoryAction.SHIFT_CLICK);
            return true;
        }
        if (craftable) {
            int amount = bulk ? stackSize(key) : unit(key);
            boolean autoStart = Settings.client().get(SKIP_CONFIRM);
            pending = new Pending(key, amount, autoStart, System.currentTimeMillis() + PENDING_MS, null);
            menu.handleInteraction(entry.getSerial(), InventoryAction.AUTO_CRAFT);
            return true;
        }
        // パターンは無いが、作業台のレシピで材料が揃うなら、クラフト端末の枠で作る(GridCraft)
        if (!stored && menu instanceof CraftingTermMenu craftingMenu
                && Features.guard(EmiAeFeatures.GRID_CRAFT, () -> GridCraft.tryStart(craftingMenu, hovered.getStack()),
                        false)) {
            return true;
        }
        say(mc, stored ? "gomacraft_utility.ae_quick_craft.not_an_item" : "gomacraft_utility.ae_quick_craft.unavailable",
                key);
        return true;
    }

    private static void say(Minecraft mc, String translationKey, AEKey key) {
        mc.player.displayClientMessage(Component.translatable(translationKey, key.getDisplayName()), true);
    }

    /**
     * 依頼した品の数の画面が開いたら、数を入れて確定する。{@code autoStart} が偽なら計画の画面で止まり(人が確定する)、
     * 真なら「すぐ始める」で作り始める。
     */
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
            screen.getMenu().confirm(p.amount(), false, p.autoStart());
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
