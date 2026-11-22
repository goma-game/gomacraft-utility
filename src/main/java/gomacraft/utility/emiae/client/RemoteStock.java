package gomacraft.utility.emiae.client;

import appeng.api.stacks.AEKey;
import gomacraft.utility.emiae.RemoteStockCache;
import gomacraft.utility.emiae.StockIndex;
import gomacraft.utility.emiae.net.StockPayloads;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 端末を閉じているときの在庫数(サーバへ問い合わせた答えの写し)。
 *
 * <p>問うのは一覧に見えている品だけ(描画で求められた品を溜め、2 秒に 1 回まで送る)。サーバが届かないと
 * 答えたら、しばらく問わない。⚠ ここでも EMI の手持ちには何も入れない(goma-org/gomabase#2286)。
 *
 * <p>⚠ 画面 → サーバの payload には大きさの上限がある(Minecraft の custom payload は 32767 バイト。越えると送る側で
 * 組み立てに失敗し、接続ごと切れうる)。品の鍵は成分(エンチャント・中身など)で大きくなるので、1 回の問いは
 * {@link #QUERY_BUDGET_BYTES} に収まる分だけ送り、残りは次の回へ回す。1 品で {@link #KEY_LIMIT_BYTES} を越える品は問わない。
 */
public final class RemoteStock {
    static final RemoteStockCache<AEKey> CACHE = new RemoteStockCache<>(
            /* 答えの寿命 */ 5000, /* 送る間隔(サーバの下限 1 秒より長く) */ 2000, /* 届かないときの間 */ 10000,
            /* 返事を待つ期限(捨てられた問いを宙に浮かせない) */ 5000, StockPayloads.MAX_KEYS);
    /** 1 回の問いの鍵の大きさの合計の上限(32767 バイトの上限に、数え方の違いの余裕を持たせる)。 */
    static final int QUERY_BUDGET_BYTES = 24000;
    /** 1 品の鍵の大きさの上限(これを越える品は、在庫数を出さない)。 */
    static final int KEY_LIMIT_BYTES = 4000;
    /** 大きすぎて問わない品(つながりが変わったら捨てる)。 */
    private static final Set<AEKey> TOO_BIG = Collections.synchronizedSet(new HashSet<>());
    private static ClientPacketListener connection;

    private RemoteStock() {
    }

    /** 描画から: 品の数(知らなければ null)。知らない・古い品は次の問い合わせに入る。 */
    static StockIndex.Stock get(AEKey key, long now) {
        if (TOO_BIG.contains(key)) {
            return null;
        }
        return CACHE.get(key, now);
    }

    /** 描画の最後に: 溜まった品を、間隔が来ていれば送る。サーバにこの Mod の口が無ければ何もしない。 */
    static void flush(long now) {
        ClientPacketListener current = Minecraft.getInstance().getConnection();
        if (current != connection) {
            // 入り直した・別のサーバへ移った: 前の答えを捨てる
            CACHE.clear();
            TOO_BIG.clear();
            connection = current;
        }
        if (current == null || !current.hasChannel(StockPayloads.Query.TYPE)) {
            return;
        }
        List<AEKey> keys = CACHE.takeDue(now);
        if (keys.isEmpty()) {
            return;
        }
        List<AEKey> send = new ArrayList<>(keys.size());
        List<AEKey> later = new ArrayList<>();
        int total = 0;
        for (AEKey key : keys) {
            int size = encodedSize(key, current);
            if (size > KEY_LIMIT_BYTES) {
                TOO_BIG.add(key);
            } else if (total + size > QUERY_BUDGET_BYTES) {
                later.add(key);
            } else {
                send.add(key);
                total += size;
            }
        }
        CACHE.putBack(later);
        if (!send.isEmpty()) {
            PacketDistributor.sendToServer(new StockPayloads.Query(send));
        }
    }

    private static int encodedSize(AEKey key, ClientPacketListener connection) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess());
        try {
            AEKey.writeKey(buf, key);
            return buf.writerIndex();
        } finally {
            buf.release();
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
