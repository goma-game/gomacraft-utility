package gomacraft.utility.emiae.client;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.core.AEConfig;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.screen.EmiScreenManager;
import gomacraft.utility.Features;
import gomacraft.utility.emiae.BadgeLayout;
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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

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
    private static final BadgeLayout.AmountLabelCache<AEKey> AMOUNT_LABELS =
            new BadgeLayout.AmountLabelCache<>(KEY_CACHE_LIMIT);

    private StockBadges() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> resetCaches());
        Features.registry().onActiveChange(EmiAeFeatures.STOCK, active -> {
            if (!active) {
                resetCaches();
            }
        });
    }

    private static void resetCaches() {
        KEYS.clear();
        AMOUNT_LABELS.clear();
        INDEX.clear();
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
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean largeFonts = AEConfig.instance().isUseLargeFonts();
        AmountFormat format = largeFonts ? AmountFormat.SLOT_LARGE_FONT : AmountFormat.SLOT;
        GuiGraphics graphics = context.raw();
        PoseStack pose = graphics.pose();
        List<? extends EmiIngredient> stacks = space.getStacks();
        int i = startIndex;
        MultiBufferSource.BufferSource buffer = null;
        Matrix4f mainMatrix = null;
        Matrix4f smallMatrix = null;
        boolean blendDisabled = false;
        // 品の絵より上に描く高さ(z + 200)を行列に乗せ、テキスト描画を 1 バッチにまとめる
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
                    if (stock == null || (stock.stored() <= 0 && !stock.craftable())) {
                        continue;
                    }
                    if (buffer == null) {
                        Matrix4f base = pose.last().pose();
                        float mainScale = BadgeLayout.scale(largeFonts);
                        mainMatrix = new Matrix4f(base)
                                .translate(0.0f, 0.0f, BadgeLayout.Z_OFFSET)
                                .scale(mainScale, mainScale, mainScale);
                        smallMatrix = largeFonts
                                ? new Matrix4f(base)
                                        .translate(0.0f, 0.0f, BadgeLayout.Z_OFFSET)
                                        .scale(BadgeLayout.SCALE_SMALL, BadgeLayout.SCALE_SMALL, BadgeLayout.SCALE_SMALL)
                                : mainMatrix;
                        buffer = mc.renderBuffers().bufferSource();
                        RenderSystem.disableBlend();
                        blendDisabled = true;
                    }
                    // EMI は品を (cx + 1, cy + 1) に 16 × 16 で描く
                    int x = space.getX(xo, yo) + 1;
                    int y = space.getY(xo, yo) + 1;
                    drawBadge(font, buffer, mainMatrix, smallMatrix, largeFonts, format, key, x, y, stock);
                }
            }
        } finally {
            try {
                if (buffer != null) {
                    buffer.endBatch();
                }
            } finally {
                if (blendDisabled) {
                    RenderSystem.enableBlend();
                }
                pose.popPose();
            }
        }
        if (remote) {
            RemoteStock.flush(now);
        }
    }

    /**
     * AE2 の端末の品の欄と同じ描き方にする({@code MEStorageScreen#renderSlot} と同じ並び。字の大きさ・位置・数の書き方は
     * AE2 自身の {@code StackSizeRenderer} と {@code formatAmount} 準拠で、AE2 の設定「大きな字」にも従う)。
     *
     * <ul>
     *   <li>在庫が無くて作れる: 右下に「+」だけ。</li>
     *   <li>在庫が在る: 右下に数。作れるなら、左上に小さな「+」も。</li>
     * </ul>
     */
    private static void drawBadge(Font font, MultiBufferSource.BufferSource buffer, Matrix4f mainMatrix,
            Matrix4f smallMatrix, boolean largeFonts, AmountFormat format, AEKey key, int x, int y,
            StockIndex.Stock stock) {
        if (stock.stored() <= 0) {
            if (stock.craftable()) {
                drawBatchText(font, buffer, mainMatrix, x, y, "+", largeFonts);
            }
            return;
        }
        String label = AMOUNT_LABELS.getOrFormat(key, stock.stored(), largeFonts,
                () -> key.formatAmount(stock.stored(), format));
        drawBatchText(font, buffer, mainMatrix, x, y, label, largeFonts);
        if (stock.craftable()) {
            drawBatchText(font, buffer, smallMatrix, x - 11, y - 11, "+", false);
        }
    }

    private static void drawBatchText(Font font, MultiBufferSource.BufferSource buffer, Matrix4f matrix,
            float x, float y, String text, boolean largeFonts) {
        int drawX = BadgeLayout.labelX(x, font.width(text), largeFonts);
        int drawY = BadgeLayout.labelY(y, largeFonts);
        font.drawInBatch(text, drawX + 1, drawY + 1, BadgeLayout.COLOR_SHADOW, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, BadgeLayout.FULL_BRIGHT);
        font.drawInBatch(text, drawX, drawY, BadgeLayout.COLOR_TEXT, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, BadgeLayout.FULL_BRIGHT);
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
