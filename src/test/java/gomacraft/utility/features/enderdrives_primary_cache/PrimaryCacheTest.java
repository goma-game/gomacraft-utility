package gomacraft.utility.features.enderdrives_primary_cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PrimaryCacheTest {
    @Test
    void reusesTheAnswerOnlyWithinTheSameTick() {
        PrimaryCache c = new PrimaryCache();
        Object cell = new Object();
        c.put(cell, 100, true);
        assertEquals(Boolean.TRUE, c.get(cell, 100));
        assertNull(c.get(cell, 101), "次の tick では使わない");
        assertNull(c.get(new Object(), 100), "ほかのセルの答えを使わない");
    }

    @Test
    void invalidateDropsEveryAnswerBeforeIt() {
        PrimaryCache c = new PrimaryCache();
        Object a = new Object();
        Object b = new Object();
        c.put(a, 5, true);
        c.put(b, 5, false);
        c.invalidate();
        assertNull(c.get(a, 5), "捨てた後に、同じ tick の古い答えを返した");
        assertNull(c.get(b, 5));
        c.put(a, 5, false);
        assertEquals(Boolean.FALSE, c.get(a, 5), "捨てた後に覚え直した答えは使う");
    }

    @Test
    void theCachedAnswerEqualsTheOriginalAndSavesRecounts() {
        // 元の答え(数え直し)を数えて、同じ tick の 2 回目からは呼ばれず、答えが同じこと
        AtomicInteger recounts = new AtomicInteger();
        Object cell = new Object();
        EnderDrivesPrimaryCache.CACHE.invalidate();
        for (int i = 0; i < 50; i++) {
            boolean answer = EnderDrivesPrimaryCache.answer(cell, 7, () -> {
                recounts.incrementAndGet();
                return false;
            });
            assertEquals(false, answer);
        }
        assertEquals(1, recounts.get(), "同じ tick で数え直した");
        // ドライブが変わった(捨てる時点): 次の問いで数え直し、新しい答えになる
        EnderDrivesPrimaryCache.CACHE.invalidate();
        assertEquals(true, EnderDrivesPrimaryCache.answer(cell, 7, () -> {
            recounts.incrementAndGet();
            return true;
        }));
        assertEquals(2, recounts.get());
    }

    @Test
    void staysBounded() {
        PrimaryCache c = new PrimaryCache();
        Object[] keep = new Object[PrimaryCache.LIMIT + 10];
        for (int i = 0; i < keep.length; i++) {
            keep[i] = new Object();
            c.put(keep[i], 1, true);
        }
        assertTrue(c.size() <= PrimaryCache.LIMIT, "上限を越えて溜めた: " + c.size());
    }
}
