package gomacraft.utility.features.enderdrives_primary_cache;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * mixin が通る道の中身(Minecraft・EnderDrives に依らない。試験はここを見る)。
 *
 * <ul>
 *   <li>{@link #hit}: isPrimary の頭で呼ぶ。覚えた答えがあれば、元の処理と同じく重なりの印を付け直して答えを返す。</li>
 *   <li>{@link #remember}: isPrimary の終わり(全部を数えた後の返り)で呼ぶ。</li>
 *   <li>tick が負(サーバの糸の上でない)なら、覚えも返しもしない。</li>
 * </ul>
 */
public final class PrimaryCacheLogic {
    private final PrimaryCache cache;
    private final LongSupplier tick;

    public PrimaryCacheLogic(PrimaryCache cache, LongSupplier tick) {
        this.cache = cache;
        this.tick = tick;
    }

    /**
     * @param markDuplicate 重なりの印を付ける(EnderDrives の {@code setDuplicateOnGrid})
     * @return 覚えた答え(無ければ null = 元の処理で数え直す)
     */
    public Boolean hit(Object cell, Consumer<Boolean> markDuplicate) {
        long now = tick.getAsLong();
        if (now < 0) {
            return null;
        }
        Boolean primary = cache.get(cell, now);
        if (primary != null) {
            markDuplicate.accept(!primary);
        }
        return primary;
    }

    public void remember(Object cell, boolean primary) {
        long now = tick.getAsLong();
        if (now >= 0) {
            cache.put(cell, now, primary);
        }
    }

    public void invalidate() {
        cache.invalidate();
    }
}
