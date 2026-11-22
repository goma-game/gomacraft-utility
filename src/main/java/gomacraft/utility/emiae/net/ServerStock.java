package gomacraft.utility.emiae.net;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.items.tools.powered.WirelessTerminalItem;
import gomacraft.utility.Features;
import gomacraft.utility.emiae.EmiAeFeatures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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
 * <p>答えるのは、その人が持つワイヤレスの端末がつながるネットワークだけで、AE2 が端末を開かせるときと同じ
 * 条件(つないだ先が在る・端末に電力が在る・同じ世界の、動いているアクセスポイントの範囲の中)を見る。
 * ⚠ 範囲の外では答えない(端末を開けない所で在庫を見せると、遊び方が変わる)。
 *
 * <p>重さ: 1 人あたり {@link #MIN_INTERVAL_TICKS} に 1 回まで、1 回に {@link StockPayloads#MAX_KEYS} 品まで。
 * 1 品は AE2 の集計({@code getCachedInventory})とパターンの表を 1 回ずつ引くだけ。
 * ⚠ ただし AE2 は、ネットワークに在庫の見張り(レベルエミッタなど)が無いと集計を毎 tick 捨て、引かれたときに
 * 全部の倉庫をなめて作り直す({@code StorageService#onServerEndTick})。そのとき 1 回の問い合わせの重さは、
 * 端末を開いている人の 1 tick 分と同じ程度になる。間隔の下限はそのために長めに取る。
 */
public final class ServerStock {
    /** 1 人あたりの問い合わせの間隔の下限(1 秒)。これより早い問い合わせは黙って捨てる。 */
    static final int MIN_INTERVAL_TICKS = 20;
    private static final Map<ServerPlayer, Integer> LAST_TICK = new WeakHashMap<>();

    private ServerStock() {
    }

    /** 機能の初期化(サーバと画面の両方で、通信の口を同じ形で登録する)。 */
    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, ServerStock::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        // optional: この Mod の無い相手とも接続できる(送る前に相手の口を確かめる)
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(StockPayloads.Query.TYPE, StockPayloads.Query.CODEC, ServerStock::handle);
        registrar.playToClient(StockPayloads.Reply.TYPE, StockPayloads.Reply.CODEC,
                (reply, context) -> Features.guard(EmiAeFeatures.STOCK,
                        () -> gomacraft.utility.emiae.client.RemoteStock.accept(reply)));
    }

    private static void handle(StockPayloads.Query query, IPayloadContext context) {
        Features.guard(EmiAeFeatures.STOCK_QUERY, () -> {
            if (!(context.player() instanceof ServerPlayer player) || !allow(player)) {
                return;
            }
            if (!player.connection.hasChannel(StockPayloads.Reply.TYPE)) {
                return;
            }
            context.reply(answer(player, query.keys()));
        });
    }

    /** 間隔の下限を見て、通すなら時刻を記す。 */
    static boolean allow(ServerPlayer player) {
        int now = player.server.getTickCount();
        synchronized (LAST_TICK) {
            Integer last = LAST_TICK.get(player);
            if (last != null && now - last < MIN_INTERVAL_TICKS && now >= last) {
                return false;
            }
            LAST_TICK.put(player, now);
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
            IGrid grid = terminal.getLinkedGrid(stack, player.level(), null);
            if (grid != null && terminal.hasPower(player, 0.5, stack) && inRange(grid, player)) {
                return grid;
            }
        }
        return null;
    }

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

    /** AE2 の WirelessTerminalMenuHost#getAccessPointSignal と同じ見方(同じ世界・動いている・範囲の 2 乗の内)。 */
    private static boolean inRange(IGrid grid, ServerPlayer player) {
        for (WirelessAccessPointBlockEntity wap : grid.getMachines(WirelessAccessPointBlockEntity.class)) {
            var location = wap.getLocation();
            if (location.getLevel() != player.level() || !wap.isActive()) {
                continue;
            }
            double dx = location.getPos().getX() - player.getX();
            double dy = location.getPos().getY() - player.getY();
            double dz = location.getPos().getZ() - player.getZ();
            double range = wap.getRange();
            if (dx * dx + dy * dy + dz * dz < range * range) {
                return true;
            }
        }
        return false;
    }
}
