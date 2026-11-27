package gomacraft.utility.features.enderdrives_primary_cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * mixin の道(頭で {@code hit}・全部を数えた後で {@code remember})をそのまま回す: 元の isPrimary の代わりに、数え直しの
     * 回数と、重なりの印の付け方を数える。
     */
    private static boolean isPrimary(PrimaryCacheLogic logic, Object cell, List<Boolean> marks,
            AtomicInteger recounts, boolean original) {
        Boolean hit = logic.hit(cell, marks::add);
        if (hit != null) {
            return hit;
        }
        recounts.incrementAndGet();
        // 元の処理: 全部を数えた後に印を付けて返す(ここを mixin の TAIL が覚える)
        marks.add(!original);
        logic.remember(cell, original);
        return original;
    }

    @Test
    void theMixinPathReturnsTheOriginalAnswerAndMarksLikeTheOriginal() {
        long[] tick = {7};
        PrimaryCacheLogic logic = new PrimaryCacheLogic(new PrimaryCache(), () -> tick[0]);
        Object cell = new Object();
        List<Boolean> marks = new ArrayList<>();
        AtomicInteger recounts = new AtomicInteger();
        for (int i = 0; i < 50; i++) {
            assertEquals(false, isPrimary(logic, cell, marks, recounts, false));
        }
        assertEquals(1, recounts.get(), "同じ tick で数え直した");
        // 当たったときも、元と同じ印(代表でない = 重なり true)を毎回付け直す
        assertEquals(50, marks.size());
        assertTrue(marks.stream().allMatch(m -> m), marks.toString());
        // ドライブが変わった(捨てる時点): 同じ tick でも数え直し、新しい答えになる
        logic.invalidate();
        assertEquals(true, isPrimary(logic, cell, marks, recounts, true));
        assertEquals(2, recounts.get());
        assertEquals(false, marks.get(marks.size() - 1));
        // 次の tick は数え直す
        tick[0] = 8;
        isPrimary(logic, cell, marks, recounts, true);
        assertEquals(3, recounts.get());
    }

    @Test
    void offTheServerThreadNothingIsRememberedOrReturned() {
        PrimaryCacheLogic logic = new PrimaryCacheLogic(new PrimaryCache(), () -> -1);
        Object cell = new Object();
        logic.remember(cell, true);
        List<Boolean> marks = new ArrayList<>();
        assertNull(logic.hit(cell, marks::add));
        assertTrue(marks.isEmpty(), "当たっていないのに印に触った");
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

    @Test
    void usesReferenceIdentityNotEquals() {
        final class CollidingCell {
            @Override
            public int hashCode() {
                return 42;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof CollidingCell;
            }
        }
        PrimaryCache c = new PrimaryCache();
        CollidingCell a = new CollidingCell();
        CollidingCell b = new CollidingCell();
        c.put(a, 1, true);
        c.put(b, 1, false);
        assertEquals(Boolean.TRUE, c.get(a, 1));
        assertEquals(Boolean.FALSE, c.get(b, 1));
    }
}
