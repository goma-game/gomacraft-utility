package gomacraft.utility.emiae.client;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import appeng.integration.modules.itemlists.CraftingHelper;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.slot.CraftingTermSlot;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import gomacraft.utility.Features;
import gomacraft.utility.emiae.EmiAeFeatures;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * パターンは無いが、ネットワーク(と手持ち)の材料で作業台のレシピから作れる品を、クラフト端末の 3×3 の枠で作って手元へ
 * ({@link QuickCraft} の続き。在庫も自動クラフトも無いときに試す)。
 *
 * <ol>
 *   <li>EMI の作業台のレシピのうち、材料が全部ネットワークか手持ちに在るものを選ぶ(AE2 自身の
 *       {@code CraftingTermMenu#findMissingIngredients}。端末の手元の写しを読むだけ)。</li>
 *   <li>AE2 自身のレシピ移し({@code CraftingHelper#performTransfer} = {@code FillCraftingGridFromRecipePacket})で枠へ
 *       材料を詰める。材料の運びと権限・接続の確かめは AE2 のサーバがする。枠に前から在った品は、AE2 がネットワークへ戻す
 *       (EMI の「+」で移すときと同じ)。</li>
 *   <li>出来上がりの枠に品が出たら、AE2 自身の操作({@code InventoryActionPacket})で取る: Alt + クリックは 1 回分を
 *       カーソルへ({@code CRAFT_ITEM})、Shift も押せば 1 スタック分を手持ちへ({@code CRAFT_SHIFT})。</li>
 * </ol>
 *
 * <p>⚠ EMI の手持ち・「作れるレシピ」は使わない(goma-org/gomabase#2286)。レシピは押したときに 1 回だけ引く。
 */
public final class GridCraft {
    /** 枠を詰めてから出来上がりが出るまで待つ時間。 */
    private static final long PENDING_MS = 3000;

    private record Pending(CraftingTermMenu menu, ItemStack output, InventoryAction action, long deadline) {
    }

    private static Pending pending;

    private GridCraft() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> Features.guard(EmiAeFeatures.GRID_CRAFT, GridCraft::tick));
        // 設定の画面で止めた・動かし直した: 出来上がりの待ちを捨てる(古い画面を持ち続けず、期限切れの知らせも出さない)
        Features.registry().onActiveChange(EmiAeFeatures.GRID_CRAFT, active -> pending = null);
    }

    /**
     * 作業台のレシピで作れるなら、枠を詰めて true。作れなければ何もせず false。
     *
     * @param hovered EMI の一覧で押した品
     */
    static boolean tryStart(CraftingTermMenu menu, EmiIngredient hovered) {
        List<EmiStack> stacks = hovered.getEmiStacks();
        if (stacks.size() != 1 || stacks.get(0).getItemStack().isEmpty()) {
            return false;
        }
        EmiStack wanted = stacks.get(0);
        for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipesByOutput(wanted)) {
            if (!recipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING) || recipe.getOutputs().isEmpty()) {
                continue;
            }
            ItemStack output = recipe.getOutputs().get(0).getItemStack();
            // 成分(components)まで比べる(同じ item で成分の違う品 —— 色・中身・エンチャントなど —— のレシピを選ばない)
            if (!ItemStack.isSameItemSameComponents(output, wanted.getItemStack())) {
                continue;
            }
            RecipeHolder<?> holder = recipe.getBackingRecipe();
            if (holder == null) {
                // 実体の無い(EMI が合成した)レシピは、AE2 へ渡す形を作れないので飛ばす
                continue;
            }
            Map<Integer, Ingredient> slots = EmiUseCraftingRecipeHandler.getGuiSlotToIngredientMap(holder.value());
            if (slots.isEmpty() || slots.keySet().stream().anyMatch(s -> s < 0 || s >= 9)) {
                continue;
            }
            if (menu.findMissingIngredients(slots).anyMissingOrCraftable()) {
                continue;
            }
            CraftingHelper.performTransfer(menu, holder.id(), holder.value(), false);
            InventoryAction action = Screen.hasShiftDown() ? InventoryAction.CRAFT_SHIFT : InventoryAction.CRAFT_ITEM;
            pending = new Pending(menu, output.copy(), action, System.currentTimeMillis() + PENDING_MS);
            return true;
        }
        return false;
    }

    /** 枠を詰めた後、出来上がりの枠に狙いの品が出たら取る。端末を閉じた・時間切れなら諦める(材料は枠に残る)。 */
    private static void tick() {
        Pending p = pending;
        if (p == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != p.menu()) {
            pending = null;
            return;
        }
        if (System.currentTimeMillis() > p.deadline()) {
            pending = null;
            // 黙って止まらない: 枠は詰めたが出来上がりが出なかった(材料は枠に残る)
            mc.player.displayClientMessage(
                    Component.translatable("gomacraft_utility.ae_grid_craft.timeout", p.output().getHoverName()), true);
            return;
        }
        for (Slot slot : p.menu().slots) {
            if (slot instanceof CraftingTermSlot && ItemStack.isSameItemSameComponents(slot.getItem(), p.output())) {
                pending = null;
                PacketDistributor.sendToServer(new InventoryActionPacket(p.action(), slot.index, 0));
                return;
            }
        }
    }
}
