package gomacraft.utility.emiae;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * サーバへ問い合わせた在庫数の写し(Minecraft に依らない。試験はここを見る)。
 *
 * <ul>
 *   <li>{@link #get}: 知っている数を返す(古くても返す。数が点滅しないように)。知らない・古い品は「問う品」に溜める。
 *       ただし返事を待っている品は溜めない(同じ品を 1 周期に 2 回送らない)。</li>
 *   <li>{@link #takeDue}: 送る間隔が来ていれば、溜まった品を上限まで取り出し、返事を待つ品として時刻と一緒に覚える。
 *       ⚠ 返事の待ちには期限を付ける(サーバは間隔の下限を割った問いを黙って捨てるので、期限が無いと宙に浮く)。</li>
 *   <li>{@link #unreachable}: サーバが届かないと答えたら、数を消し、しばらく問わない。</li>
 * </ul>
 *
 * @param <K> 品の鍵
 */
public final class RemoteStockCache<K> {
    private record Known(StockIndex.Stock stock, long at) {
    }

    private final long ttlMs;
    private final long sendIntervalMs;
    private final long unreachableBackoffMs;
    private final long replyTimeoutMs;
    private final int maxKeys;
    private final Map<K, Known> known = new HashMap<>();
    private final Set<K> wanted = new LinkedHashSet<>();
    /** 送って返事を待っている品 → 送った時刻。 */
    private final Map<K, Long> inFlight = new HashMap<>();
    private long lastSent = Long.MIN_VALUE / 2;
    private long quietUntil = Long.MIN_VALUE / 2;

    /**
     * @param replyTimeoutMs 返事を待つ期限。これを過ぎた品は、また問う品に戻れる
     */
    public RemoteStockCache(long ttlMs, long sendIntervalMs, long unreachableBackoffMs, long replyTimeoutMs,
            int maxKeys) {
        this.ttlMs = ttlMs;
        this.sendIntervalMs = sendIntervalMs;
        this.unreachableBackoffMs = unreachableBackoffMs;
        this.replyTimeoutMs = replyTimeoutMs;
        this.maxKeys = maxKeys;
    }

    public StockIndex.Stock get(K key, long now) {
        if (key == null) {
            return null;
        }
        Known k = known.get(key);
        if ((k == null || now - k.at() >= ttlMs) && !waiting(key, now) && wanted.size() < maxKeys * 4) {
            wanted.add(key);
        }
        if (k == null || (k.stock().stored() <= 0 && !k.stock().craftable())) {
            return null;
        }
        // 古い数は寿命の間は出し続けるが、寿命の STALE_FACTOR 倍を過ぎたら出さない(サーバが答えなくなったのに、
        // 古い数が出続けないように)
        if (now - k.at() >= ttlMs * STALE_FACTOR) {
            return null;
        }
        return k.stock();
    }

    /** 古い数を出し続ける上限(寿命の何倍まで)。 */
    static final int STALE_FACTOR = 3;

    /**
     * {@link #takeDue} で取り出したが送らなかった品を、問う品へ戻す(送れる大きさに収まらなかった分)。返事の待ちからも外す。
     */
    public void putBack(List<K> keys) {
        for (K key : keys) {
            inFlight.remove(key);
            wanted.add(key);
        }
    }

    private boolean waiting(K key, long now) {
        Long sent = inFlight.get(key);
        if (sent == null) {
            return false;
        }
        if (now - sent >= replyTimeoutMs) {
            inFlight.remove(key);
            return false;
        }
        return true;
    }

    public List<K> takeDue(long now) {
        if (wanted.isEmpty() || now < quietUntil || now - lastSent < sendIntervalMs) {
            return List.of();
        }
        List<K> keys = new ArrayList<>(Math.min(wanted.size(), maxKeys));
        var it = wanted.iterator();
        while (it.hasNext() && keys.size() < maxKeys) {
            K key = it.next();
            it.remove();
            keys.add(key);
            inFlight.put(key, now);
        }
        lastSent = now;
        if (inFlight.size() > maxKeys * 16) {
            // 返事が来ないまま溜まった分(期限で外れるはずだが、念のため)
            inFlight.clear();
        }
        return keys;
    }

    public void put(K key, StockIndex.Stock stock, long now) {
        inFlight.remove(key);
        known.put(key, new Known(stock, now));
        if (known.size() > maxKeys * 64) {
            // 長く遊ぶと見た品が溜まる。古いものから捨てる手間は惜しんで、溢れたら全部捨てて問い直す
            known.clear();
        }
    }

    public void unreachable(long now) {
        known.clear();
        wanted.clear();
        inFlight.clear();
        quietUntil = now + unreachableBackoffMs;
    }

    public void clear() {
        known.clear();
        wanted.clear();
        inFlight.clear();
        lastSent = Long.MIN_VALUE / 2;
        quietUntil = Long.MIN_VALUE / 2;
    }
}
