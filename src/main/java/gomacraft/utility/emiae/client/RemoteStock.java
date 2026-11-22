package gomacraft.utility.emiae.client;

import appeng.api.stacks.AEKey;
import gomacraft.utility.emiae.RemoteStockCache;
import gomacraft.utility.emiae.StockIndex;
import gomacraft.utility.emiae.net.StockPayloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 端末を閉じているときの在庫数(サーバへ問い合わせた答えの写し)。
 *
 * <p>問うのは一覧に見えている品だけ(描画で求められた品を溜め、2 秒に 1 回まで送る)。サーバが届かないと
 * 答えたら、しばらく問わない。⚠ ここでも EMI の手持ちには何も入れない(goma-org/gomabase#2286)。
 */
public final class RemoteStock {
    static final RemoteStockCache<AEKey> CACHE = new RemoteStockCache<>(
            /* 答えの寿命 */ 5000, /* 送る間隔(サーバの下限 1 秒より長く) */ 2000, /* 届かないときの間 */ 10000,
            StockPayloads.MAX_KEYS);
    private static ClientPacketListener connection;

    private RemoteStock() {
    }

    /** 描画から: 品の数(知らなければ null)。知らない・古い品は次の問い合わせに入る。 */
    static StockIndex.Stock get(AEKey key, long now) {
        return CACHE.get(key, now);
    }

    /** 描画の最後に: 溜まった品を、間隔が来ていれば送る。サーバにこの Mod の口が無ければ何もしない。 */
    static void flush(long now) {
        ClientPacketListener current = Minecraft.getInstance().getConnection();
        if (current != connection) {
            // 入り直した・別のサーバへ移った: 前の答えを捨てる
            CACHE.clear();
            connection = current;
        }
        if (current == null || !current.hasChannel(StockPayloads.Query.TYPE)) {
            return;
        }
        List<AEKey> keys = CACHE.takeDue(now);
        if (!keys.isEmpty()) {
            PacketDistributor.sendToServer(new StockPayloads.Query(keys));
        }
    }

    /** サーバの答え(通信の糸から呼ばれる。NeoForge は既定で主の糸へ回してから呼ぶ)。 */
    public static void accept(StockPayloads.Reply reply) {
        long now = System.currentTimeMillis();
        if (!reply.reachable()) {
            CACHE.unreachable(now);
            return;
        }
        for (StockPayloads.Entry entry : reply.entries()) {
            if (entry.key() != null) {
                CACHE.put(entry.key(), new StockIndex.Stock(entry.stored(), entry.craftable()), now);
            }
        }
    }
}
