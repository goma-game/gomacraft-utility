package gomacraft.utility.features.enderdrives_primary_cache;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * EnderDrives の「このセルがその grid で代表か」の答えを、同じ tick の中で使い回す表(Minecraft に依らない。試験はここを見る)。
 *
 * <p>答えを捨てる時点は 2 つ:
 * <ul>
 *   <li>tick が進んだ(答えは同じ tick の中でだけ使う)。</li>
 *   <li>{@link #invalidate}(セルをドライブに付けた・外した、ドライブの中身・つながり・電力が変わった、grid の
 *       ノードが増えた・減った —— どれも答えを変えうる)。呼ばれたら、その時点までの答えを全部捨てる。</li>
 * </ul>
 *
 * <p>セルは弱い参照で持つ(外したセルを表に残さない)。数が {@link #LIMIT} を越えたら全部捨てる。
 */
public final class PrimaryCache {
    static final int LIMIT = 8192;

    private record Entry(long tick, long epoch, boolean primary) {
    }

    private final Map<Object, Entry> entries = new WeakHashMap<>();
    private long epoch;

    /** 同じ tick で、捨てる時点の後に覚えた答え(無ければ null)。 */
    public synchronized Boolean get(Object cell, long tick) {
        Entry e = entries.get(cell);
        if (e == null || e.tick() != tick || e.epoch() != epoch) {
            return null;
        }
        return e.primary();
    }

    public synchronized void put(Object cell, long tick, boolean primary) {
        if (entries.size() >= LIMIT) {
            entries.clear();
        }
        entries.put(cell, new Entry(tick, epoch, primary));
    }

    /** 答えを変えうることが起きた: 今までの答えを全部使わない。 */
    public synchronized void invalidate() {
        epoch++;
    }

    synchronized int size() {
        return entries.size();
    }
}
