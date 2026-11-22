package gomacraft.utility.emiae.net;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.emiae.EmiAeFeatures;
import gomacraft.utility.feature.FeatureRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * サーバの側: 端末を閉じている人の在庫数の問い合わせに答える。
 *
 * <p>答えるのは、その人が持つワイヤレスの端末が<b>今つながる</b>ネットワークだけ(ユーザ決定 goma-org/gomabase#2295:
 * 端末が実際につながる所なら見せる)。つながるかは、端末の Mod 自身の判定に任せる: 端末の item から AE2 の
 * 端末の host を作り({@code IMenuItem#getMenuHost})、その接続の状態({@code getLinkStatus})と、つながる先の口
 * ({@code getActionableNode})を読む。AE2 の端末ならアクセスポイントの範囲と電力、ae2wtlib の端末なら量子ブリッジの
 * カードの道もその Mod の判定で入る(判定を写さない)。
 * ⚠ host を作るときの電力: AE2 の端末は試しの引き出しだけで減らない。ae2wtlib の端末は、量子ブリッジにつながって
 * いる間、接続の確かめ({@code consumeIdlePower(SIMULATE)})の中で端末の充電を進める処理が走り、ネットワークの
 * 電力を引く(ae2wtlib の仕組み。問いの間隔の下限 1 秒に 1 回まで)。
 * ⚠ 1 つの端末の判定で例外が出ても(アドオンの端末の作りの違いなど)、その端末だけを飛ばす。機能ごと止めると、
 * 全員の在庫数が再起動まで消えるため。
 *
 * <p>重さ: 1 人あたり {@link #MIN_INTERVAL_MS} に 1 回まで、1 回に {@link StockPayloads#MAX_KEYS} 品まで。
 * 1 品は AE2 の集計({@code getCachedInventory})とパターンの表を 1 回ずつ引くだけ。
 * ⚠ ただし AE2 は、ネットワークに在庫の見張り(レベルエミッタなど)が無いと集計を毎 tick 捨て、引かれたときに
 * 全部の倉庫をなめて作り直す({@code StorageService#onServerEndTick})。そのとき 1 回の問い合わせの重さは、
 * 端末を開いている人の 1 tick 分と同じ程度になる。間隔の下限はそのために長めに取る。
 */
public final class ServerStock {
    /**
     * 1 人あたりの問い合わせの間隔の下限(1 秒)。これより早い問い合わせは黙って捨てる。⚠ tick ではなく時刻で数える
     * (画面はミリ秒で 2 秒おきに送る。tick で数えると、TPS が落ちたときに下限を割って捨てられ続ける)。
     */
    static final long MIN_INTERVAL_MS = 1000;
    private static final Map<ServerPlayer, Long> LAST_MS = new WeakHashMap<>();

    private ServerStock() {
    }

    /** 機能の初期化(サーバと画面の両方で、通信の口を同じ形で登録する)。 */
    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class,
                event -> Features.guard(EmiAeFeatures.STOCK_QUERY, () -> register(event)));
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        // optional: この Mod の無い相手とも接続できる(送る前に相手の口を確かめる)
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(StockPayloads.Query.TYPE, StockPayloads.Query.CODEC, ServerStock::handle);
        registrar.playToClient(StockPayloads.Reply.TYPE, StockPayloads.Reply.CODEC,
                // 通信の受け手は通信の機能の守りで包む(受け手の失敗で、描く機能 emi_ae_stock を止めない)
                (reply, context) -> Features.guard(EmiAeFeatures.STOCK_QUERY,
                        () -> gomacraft.utility.emiae.client.RemoteStock.accept(reply)));
    }

    private static void handle(StockPayloads.Query query, IPayloadContext context) {
        Features.guard(EmiAeFeatures.STOCK_QUERY, () -> {
            if (!(context.player() instanceof ServerPlayer player) || !allow(player, System.currentTimeMillis())) {
                return;
            }
            if (!player.connection.hasChannel(StockPayloads.Reply.TYPE)) {
                return;
            }
            context.reply(answer(player, query.keys()));
        });
    }

    /** 間隔の下限を見て、通すなら時刻を記す。 */
    static boolean allow(ServerPlayer player, long now) {
        synchronized (LAST_MS) {
            Long last = LAST_MS.get(player);
            if (last != null && now - last < MIN_INTERVAL_MS && now >= last) {
                return false;
            }
            LAST_MS.put(player, now);
            return true;
        }
    }

    /** 答えを作る(自己検査からも呼ぶ)。 */
    public static StockPayloads.Reply answer(ServerPlayer player, List<AEKey> keys) {
        IGrid grid = reachableGrid(player);
        if (grid == null) {
            return StockPayloads.Reply.unreachable();
        }
        KeyCounter inventory = grid.getStorageService().getCachedInventory();
        var crafting = grid.getCraftingService();
        List<StockPayloads.Entry> entries = new ArrayList<>(Math.min(keys.size(), StockPayloads.MAX_KEYS));
        for (AEKey key : keys) {
            if (entries.size() >= StockPayloads.MAX_KEYS) {
                break;
            }
            entries.add(new StockPayloads.Entry(key, inventory.get(key), crafting.isCraftable(key)));
        }
        return new StockPayloads.Reply(true, entries);
    }

    /** その人が今、ワイヤレスの端末で開けるネットワーク(無ければ null)。 */
    static IGrid reachableGrid(ServerPlayer player) {
        for (ItemStack stack : carried(player)) {
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) {
                continue;
            }
            try {
                // 端末の Mod 自身の判定(AE2 は範囲と電力、ae2wtlib は量子ブリッジのカードも)。host は作るときに
                // 接続を調べる(WirelessTerminalMenuHost の組み立て)
                if (terminal.getMenuHost(player, MenuLocators.forStack(stack), null) instanceof WirelessTerminalMenuHost<?> host
                        && host.getLinkStatus().connected()) {
                    IGridNode node = host.getActionableNode();
                    if (node != null && node.getGrid() != null) {
                        return node.getGrid();
                    }
                }
            } catch (RuntimeException e) {
                // その端末だけ飛ばす。ログは品の種類ごとに最初の 1 回だけ(問いのたびに書かない)
                String item = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
                if (LOGGED_TERMINALS.add(item)) {
                    LOGGER.warn("gomacraft_utility: ワイヤレスの端末 {} の接続を調べられないので飛ばす(以後この品は書かない): {}",
                            item, FeatureRegistry.describe(e));
                }
            }
        }
        return null;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GomacraftUtility.MOD_ID);
    private static final Set<String> LOGGED_TERMINALS = ConcurrentHashMap.newKeySet();

    private static List<ItemStack> carried(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        var inventory = player.getInventory();
        stacks.addAll(inventory.items);
        stacks.addAll(inventory.offhand);
        if (ModList.get().isLoaded("curios")) {
            stacks.addAll(CuriosStacks.of(player));
        }
        return stacks;
    }
}
