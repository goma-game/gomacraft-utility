package gomacraft.utility.emiae;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** {@link BadgeLayout} の座標計算と数量キャッシュを検証する。 */
class BadgeLayoutTest {
    @Test
    void matchesAe2StackSizeRendererCoordinates() {
        // 通常フォント: scale=0.666f, offset=-1
        assertEquals((int) ((10.0f - 1.0f + 16.0f + 2.0f - 12 * 0.666f) * (1.0f / 0.666f)),
                BadgeLayout.labelX(10.0f, 12, false));
        assertEquals((int) ((20.0f - 1.0f + 16.0f - 5.0f * 0.666f) * (1.0f / 0.666f)),
                BadgeLayout.labelY(20.0f, false));
        // 大きいフォント: scale=0.85f, offset=0
        assertEquals((int) ((10.0f + 16.0f + 2.0f - 12 * 0.85f) * (1.0f / 0.85f)),
                BadgeLayout.labelX(10.0f, 12, true));
        assertEquals((int) ((20.0f + 16.0f - 5.0f * 0.85f) * (1.0f / 0.85f)),
                BadgeLayout.labelY(20.0f, true));
    }

    @Test
    void cachesFormattedLabelsPerAmountAndFontSizeAndEvictsAtLimit() {
        BadgeLayout.AmountLabelCache<String> cache = new BadgeLayout.AmountLabelCache<>(2);
        AtomicInteger calls = new AtomicInteger();

        assertEquals("1.2k", cache.getOrFormat("iron", 1200, false, () -> {
            calls.incrementAndGet();
            return "1.2k";
        }));
        assertEquals("1.2k", cache.getOrFormat("iron", 1200, false, () -> {
            calls.incrementAndGet();
            return "recomputed";
        }));
        assertEquals(1, calls.get());

        // 数量またはフォントサイズが変われば再計算する
        assertEquals("1.3k", cache.getOrFormat("iron", 1300, false, () -> {
            calls.incrementAndGet();
            return "1.3k";
        }));
        assertEquals("1300", cache.getOrFormat("iron", 1300, true, () -> {
            calls.incrementAndGet();
            return "1300";
        }));
        assertEquals(3, calls.get());

        cache.getOrFormat("gold", 5, false, () -> "5");
        assertEquals(2, cache.size());
        cache.getOrFormat("copper", 8, false, () -> "8");
        assertEquals(1, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
    }
}
