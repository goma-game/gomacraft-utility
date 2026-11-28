package gomacraft.utility.features.enderdrives_primary_cache;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

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
 * <p>セルは参照同一性ベースの弱い参照で持つ(外したセルを表に残さない)。数が {@link #LIMIT} を越えたら全部捨てる。
 */
public final class PrimaryCache {
    static final int LIMIT = 8192;

    private record Entry(long tick, long epoch, boolean primary) {
    }

    private static final class IdentityWeakKey extends WeakReference<Object> {
        private final int hash;

        IdentityWeakKey(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof IdentityWeakKey other)) {
                return false;
            }
            Object referent = get();
            return referent != null && referent == other.get();
        }
    }

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private final Map<IdentityWeakKey, Entry> entries = new HashMap<>();
    private long epoch;

    private void expunge() {
        Reference<?> ref;
        while ((ref = queue.poll()) != null) {
            entries.remove(ref);
        }
    }

    /** 同じ tick で、捨てる時点の後に覚えた答え(無ければ null)。 */
    public synchronized Boolean get(Object cell, long tick) {
        expunge();
        if (cell == null) {
            return null;
        }
        Entry e = entries.get(new IdentityWeakKey(cell, null));
        if (e == null || e.tick() != tick || e.epoch() != epoch) {
            return null;
        }
        return e.primary();
    }

    public synchronized void put(Object cell, long tick, boolean primary) {
        expunge();
        if (cell == null) {
            return;
        }
        if (entries.size() >= LIMIT) {
            entries.clear();
        }
        entries.put(new IdentityWeakKey(cell, queue), new Entry(tick, epoch, primary));
    }

    /** 答えを変えうることが起きた: 今までの答えを全部使わない。 */
    public synchronized void invalidate() {
        expunge();
        epoch++;
    }

    synchronized int size() {
        expunge();
        return entries.size();
    }
}
