package gomacraft.utility.emiae;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * 端末の中身(AE2 の手元の写し)から引く、品 → 在庫数・クラフト可 の索引。
 *
 * <p>⚠ 作り直しの回数は時間で抑える({@code minIntervalMs} に 1 回まで)。ネットワークの中身の変わるたびに
 * 作り直すと、EMI Link の重さ(goma-org/gomabase#2286)と同じ型になる。作り直しは描画から呼ばれたときだけ
 * (= EMI の一覧が見えている間だけ)で、写しの全件を 1 回なめるだけ(レシピの照合はしない)。
 *
 * @param <K> 品の鍵(実物は AE2 の AEKey。試験は文字列)
 */
public final class StockIndex<K> {
    /** 在庫数と、クラフトできるか。 */
    public record Stock(long stored, boolean craftable) {
    }

    private final long minIntervalMs;
    private Map<K, Stock> map = Map.of();
    private Object source;
    private long builtAt;
    private int rebuilds;

    public StockIndex(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    /**
     * 写しが変わった(別の端末を開いた)か、前の作り直しから {@code minIntervalMs} 経っていれば作り直す。
     *
     * @param sourceId 写しの実物(同じものかを == で見る)
     * @param now      今の時刻(ミリ秒)
     * @param entries  写しの中身(作り直すときだけなめる)
     * @return 作り直したら true
     */
    public <E> boolean refresh(Object sourceId, long now, Iterable<E> entries, Function<E, K> key,
            ToLongFunction<E> stored, Predicate<E> craftable) {
        if (sourceId == source && now - builtAt < minIntervalMs) {
            return false;
        }
        Map<K, Stock> next = new HashMap<>();
        try {
            for (E e : entries) {
                K k = key.apply(e);
                if (k == null) {
                    continue;
                }
                long amount = Math.max(0, stored.applyAsLong(e));
                boolean canCraft = craftable.test(e);
                if (amount == 0 && !canCraft) {
                    continue;
                }
                next.merge(k, new Stock(amount, canCraft),
                        (a, b) -> new Stock(a.stored() + b.stored(), a.craftable() || b.craftable()));
            }
        } catch (ConcurrentModificationException e) {
            // 写しは描画と同じ糸で更新されるはずだが、万一ぶつかったら前の索引のまま次の回に回す
            return false;
        }
        map = next;
        source = sourceId;
        builtAt = now;
        rebuilds++;
        return true;
    }

    /** 端末を閉じたら呼ぶ(古い数を出さない)。 */
    public void clear() {
        if (source != null || !map.isEmpty()) {
            map = Map.of();
            source = null;
        }
    }

    public Stock get(K key) {
        return key == null ? null : map.get(key);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    /** 作り直した回数(試験と計測のため)。 */
    public int rebuilds() {
        return rebuilds;
    }
}
