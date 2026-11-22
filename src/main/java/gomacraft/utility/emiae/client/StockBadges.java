package gomacraft.utility.emiae.client;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.core.AEConfig;
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
import gomacraft.utility.Features;
import gomacraft.utility.emiae.EmiAeFeatures;
import gomacraft.utility.emiae.StockIndex;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
 * <p>数は、端末を開いている間はその手元の写し({@link MEStorageMenu#getClientRepo})から {@link StockIndex} で引き、
 * 閉じている間はサーバへ問い合わせた答え({@link RemoteStock}。サーバにこの Mod が在るときだけ)から引く。
 * ⚠ EMI の「手持ち」には何も入れない(goma-org/gomabase#2286)。描くのは EMI が描いた見えている品だけ。
 */
public final class StockBadges {
    /** 索引の作り直しは 0.5 秒に 1 回まで。 */
    static final StockIndex<AEKey> INDEX = new StockIndex<>(500);
    /** EMI の品 → AE の鍵。品の実体は一覧の中で同じものが使い回されるので、実体で覚える。 */
    private static final Map<EmiStack, Optional<AEKey>> KEYS = new IdentityHashMap<>();
    private static final int KEY_CACHE_LIMIT = 16384;

    private StockBadges() {
    }

    /** {@code EmiScreenManager.ScreenSpace#render} の最後から呼ばれる(mixin)。EMI と同じ並びで品の位置を引く。 */
    public static void render(EmiScreenManager.ScreenSpace space, EmiDrawContext context, int startIndex) {
        if (space.pageSize <= 0 || space.getType() == SidebarType.CHESS) {
            return;
        }
        long now = System.currentTimeMillis();
        Function<AEKey, StockIndex.Stock> lookup;
        boolean remote = false;
        if (refresh(now)) {
            // 端末を開いている: AE2 が端末へ送っている写しから(サーバへは問わない)
            if (INDEX.isEmpty()) {
                return;
            }
            lookup = INDEX::get;
        } else if (Features.registry().isOn(EmiAeFeatures.STOCK_QUERY)) {
            // 端末を閉じている: サーバへ問い合わせた答えの写しから(見えている品だけを問う)
            lookup = key -> RemoteStock.get(key, now);
            remote = true;
        } else {
            return;
        }
        Font font = Minecraft.getInstance().font;
        GuiGraphics graphics = context.raw();
        PoseStack pose = graphics.pose();
        List<? extends EmiIngredient> stacks = space.getStacks();
        int i = startIndex;
        // 品の絵より上に描く高さ(z + 200)は、AE2 の StackSizeRenderer が自分で上げる
        pose.pushPose();
        try {
            outer:
            for (int yo = 0; yo < space.th; yo++) {
                for (int xo = 0; xo < space.getWidth(yo); xo++) {
                    if (i >= stacks.size()) {
                        break outer;
                    }
                    EmiIngredient ingredient = stacks.get(i++);
                    AEKey key = keyOf(ingredient);
                    StockIndex.Stock stock = key == null ? null : lookup.apply(key);
                    if (stock == null) {
                        continue;
                    }
                    // EMI は品を (cx + 1, cy + 1) に 16 × 16 で描く
                    int x = space.getX(xo, yo) + 1;
                    int y = space.getY(xo, yo) + 1;
                    drawBadge(graphics, key, font, x, y, stock);
                }
            }
        } finally {
            pose.popPose();
        }
        if (remote) {
            RemoteStock.flush(now);
        }
    }

    /**
     * AE2 の端末の品の欄と同じ描き方にする({@code MEStorageScreen#renderSlot} と同じ並び。字の大きさ・位置・数の書き方は
     * AE2 自身の {@link StackSizeRenderer} と {@code formatAmount} で、AE2 の設定「大きな字」にも従う)。
     *
     * <ul>
     *   <li>在庫が無くて作れる: 右下に「+」だけ。</li>
     *   <li>在庫が在る: 右下に数。作れるなら、左上に小さな「+」も。</li>
     * </ul>
     */
    private static void drawBadge(GuiGraphics graphics, AEKey key, Font font, int x, int y, StockIndex.Stock stock) {
        boolean largeFonts = AEConfig.instance().isUseLargeFonts();
        if (stock.stored() <= 0) {
            if (stock.craftable()) {
                StackSizeRenderer.renderSizeLabel(graphics, font, x, y, "+");
            }
            return;
        }
        AmountFormat format = largeFonts ? AmountFormat.SLOT_LARGE_FONT : AmountFormat.SLOT;
        StackSizeRenderer.renderSizeLabel(graphics, font, x, y, key.formatAmount(stock.stored(), format), largeFonts);
        if (stock.craftable()) {
            StackSizeRenderer.renderSizeLabel(graphics, font, x - 11, y - 11, "+", false);
        }
    }

    /** 開いている端末の写しで索引を新しくする。端末が開いていなければ索引を空にして false。 */
    private static boolean refresh(long now) {
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
        INDEX.refresh(repo, now, repo.getAllEntries(), GridInventoryEntry::getWhat,
                GridInventoryEntry::getStoredAmount, GridInventoryEntry::isCraftable);
        return true;
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
