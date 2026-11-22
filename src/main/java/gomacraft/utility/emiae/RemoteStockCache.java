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
 *   <li>{@link #get}: 知っている数を返す(古くても返す。数が点滅しないように)。知らない・古い品は「問う品」に溜める。</li>
 *   <li>{@link #takeDue}: 送る間隔が来ていれば、溜まった品を上限まで取り出す。</li>
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
    private final int maxKeys;
    private final Map<K, Known> known = new HashMap<>();
    private final Set<K> wanted = new LinkedHashSet<>();
    private long lastSent = Long.MIN_VALUE / 2;
    private long quietUntil = Long.MIN_VALUE / 2;

    public RemoteStockCache(long ttlMs, long sendIntervalMs, long unreachableBackoffMs, int maxKeys) {
        this.ttlMs = ttlMs;
        this.sendIntervalMs = sendIntervalMs;
        this.unreachableBackoffMs = unreachableBackoffMs;
        this.maxKeys = maxKeys;
    }

    public StockIndex.Stock get(K key, long now) {
        if (key == null) {
            return null;
        }
        Known k = known.get(key);
        if ((k == null || now - k.at() >= ttlMs) && wanted.size() < maxKeys * 4) {
            wanted.add(key);
        }
        if (k == null || (k.stock().stored() <= 0 && !k.stock().craftable())) {
            return null;
        }
        return k.stock();
    }

    public List<K> takeDue(long now) {
        if (wanted.isEmpty() || now < quietUntil || now - lastSent < sendIntervalMs) {
            return List.of();
        }
        List<K> keys = new ArrayList<>(Math.min(wanted.size(), maxKeys));
        var it = wanted.iterator();
        while (it.hasNext() && keys.size() < maxKeys) {
            keys.add(it.next());
            it.remove();
        }
        lastSent = now;
        return keys;
    }

    public void put(K key, StockIndex.Stock stock, long now) {
        known.put(key, new Known(stock, now));
        if (known.size() > maxKeys * 64) {
            // 長く遊ぶと見た品が溜まる。古いものから捨てる手間は惜しんで、溢れたら全部捨てて問い直す
            known.clear();
        }
    }

    public void unreachable(long now) {
        known.clear();
        wanted.clear();
        quietUntil = now + unreachableBackoffMs;
    }

    public void clear() {
        known.clear();
        wanted.clear();
        lastSent = Long.MIN_VALUE / 2;
        quietUntil = Long.MIN_VALUE / 2;
    }
}
