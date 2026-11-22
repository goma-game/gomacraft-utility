package gomacraft.utility.emiae.client;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.screen.EmiScreenManager;
import gomacraft.utility.emiae.CountFormat;
import gomacraft.utility.emiae.StockIndex;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * EMI の一覧の品に、AE のネットワークの在庫数(右下)と「クラフト可」の印(左上の +)を描く。
 *
 * <p>数は、開いている端末の手元の写し({@link MEStorageMenu#getClientRepo})から {@link StockIndex} で引く。
 * ⚠ EMI の「手持ち」には何も入れない(goma-org/gomabase#2286)。描くのは EMI が描いた見えている品だけ。
 */
public final class StockBadges {
    /** 索引の作り直しは 0.5 秒に 1 回まで。 */
    static final StockIndex<AEKey> INDEX = new StockIndex<>(500);
    /** EMI の品 → AE の鍵。品の実体は一覧の中で同じものが使い回されるので、実体で覚える。 */
    private static final Map<EmiStack, Optional<AEKey>> KEYS = new IdentityHashMap<>();
    private static final int KEY_CACHE_LIMIT = 16384;
    private static final int CRAFTABLE_COLOR = 0xFF7FFF7F;

    private StockBadges() {
    }

    /** {@code EmiScreenManager.ScreenSpace#render} の最後から呼ばれる(mixin)。EMI と同じ並びで品の位置を引く。 */
    public static void render(EmiScreenManager.ScreenSpace space, EmiDrawContext context, int startIndex) {
        if (space.pageSize <= 0 || space.getType() == SidebarType.CHESS || !refresh()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        GuiGraphics graphics = context.raw();
        PoseStack pose = graphics.pose();
        List<? extends EmiIngredient> stacks = space.getStacks();
        int i = startIndex;
        pose.pushPose();
        // 品の絵(z ≒ 150 前後)より上に描く。バニラの個数の文字と同じ高さ
        pose.translate(0, 0, 200);
        try {
            outer:
            for (int yo = 0; yo < space.th; yo++) {
                for (int xo = 0; xo < space.getWidth(yo); xo++) {
                    if (i >= stacks.size()) {
                        break outer;
                    }
                    EmiIngredient ingredient = stacks.get(i++);
                    StockIndex.Stock stock = INDEX.get(keyOf(ingredient));
                    if (stock == null) {
                        continue;
                    }
                    // EMI は品を (cx + 1, cy + 1) に 16 × 16 で描く
                    int x = space.getX(xo, yo) + 1;
                    int y = space.getY(xo, yo) + 1;
                    drawBadge(graphics, pose, font, x, y, stock);
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawBadge(GuiGraphics graphics, PoseStack pose, Font font, int x, int y, StockIndex.Stock stock) {
        pose.pushPose();
        pose.scale(0.5f, 0.5f, 1f);
        if (stock.stored() > 0) {
            String text = CountFormat.shortCount(stock.stored());
            int width = font.width(text);
            graphics.drawString(font, text, (x + 16) * 2 - width, (y + 16) * 2 - font.lineHeight, 0xFFFFFFFF, true);
        }
        if (stock.craftable()) {
            graphics.drawString(font, "+", x * 2, y * 2, CRAFTABLE_COLOR, true);
        }
        pose.popPose();
    }

    /** 開いている端末の写しで索引を新しくする。端末が開いていなければ索引を空にして false。 */
    private static boolean refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof MEStorageMenu menu)) {
            INDEX.clear();
            return false;
        }
        IClientRepo repo = menu.getClientRepo();
        if (repo == null) {
            INDEX.clear();
            return false;
        }
        INDEX.refresh(repo, System.currentTimeMillis(), repo.getAllEntries(), GridInventoryEntry::getWhat,
                GridInventoryEntry::getStoredAmount, GridInventoryEntry::isCraftable);
        return !INDEX.isEmpty();
    }

    /** 一覧の 1 枠を AE の鍵にする(品・液体の 1 つだけの枠。タグなど複数の品の枠は null)。 */
    static AEKey keyOf(EmiIngredient ingredient) {
        if (ingredient instanceof EmiFavorite favorite) {
            ingredient = favorite.getStack();
        }
        if (ingredient == null) {
            return null;
        }
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.size() != 1) {
            return null;
        }
        EmiStack stack = stacks.get(0);
        Optional<AEKey> cached = KEYS.get(stack);
        if (cached == null) {
            if (KEYS.size() >= KEY_CACHE_LIMIT) {
                KEYS.clear();
            }
            cached = Optional.ofNullable(toKey(stack));
            KEYS.put(stack, cached);
        }
        return cached.orElse(null);
    }

    private static AEKey toKey(EmiStack stack) {
        Object key = stack.getKey();
        if (key instanceof Item) {
            return AEItemKey.of(stack.getItemStack());
        }
        if (key instanceof Fluid fluid) {
            DataComponentPatch patch = stack.getComponentChanges();
            if (patch == null || patch.isEmpty()) {
                return AEFluidKey.of(fluid);
            }
            return AEFluidKey.of(new FluidStack(fluid.builtInRegistryHolder(), 1, patch));
        }
        return null;
    }
}
