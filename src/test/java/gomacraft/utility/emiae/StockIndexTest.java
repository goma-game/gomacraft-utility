package gomacraft.utility.emiae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockIndexTest {
    record Entry(String key, long stored, boolean craftable) {
    }

    private static boolean refresh(StockIndex<String> index, Object source, long now, List<Entry> entries) {
        return index.refresh(source, now, entries, Entry::key, Entry::stored, Entry::craftable);
    }

    @Test
    void readsStoredAndCraftableAndSkipsEmptyEntries() {
        StockIndex<String> index = new StockIndex<>(500);
        Object repo = new Object();
        refresh(index, repo, 0, List.of(
                new Entry("iron", 1234, false),
                new Entry("gear", 0, true),
                new Entry("both", 5, true),
                new Entry("gone", 0, false),
                new Entry(null, 9, true)));
        assertEquals(new StockIndex.Stock(1234, false), index.get("iron"));
        assertEquals(new StockIndex.Stock(0, true), index.get("gear"));
        assertEquals(new StockIndex.Stock(5, true), index.get("both"));
        assertNull(index.get("gone"));
        assertNull(index.get(null));
        assertEquals(3, index.size());
    }

    @Test
    void rebuildsAtMostOncePerIntervalForTheSameSource() {
        StockIndex<String> index = new StockIndex<>(500);
        Object repo = new Object();
        List<Entry> entries = new ArrayList<>(List.of(new Entry("iron", 1, false)));
        assertTrue(refresh(index, repo, 1000, entries));
        entries.set(0, new Entry("iron", 2, false));
        // ネットワークの中身が変わっても、間隔の内は作り直さない(#2286 の型にしない)
        assertFalse(refresh(index, repo, 1499, entries));
        assertEquals(1, index.get("iron").stored());
        assertTrue(refresh(index, repo, 1500, entries));
        assertEquals(2, index.get("iron").stored());
        // 別の端末を開いたら、間隔の内でもすぐ作り直す
        assertTrue(refresh(index, new Object(), 1501, List.of(new Entry("gold", 3, false))));
        assertNull(index.get("iron"));
        assertEquals(3, index.rebuilds());
    }

    @Test
    void clearForgetsTheSource() {
        StockIndex<String> index = new StockIndex<>(500);
        Object repo = new Object();
        refresh(index, repo, 0, List.of(new Entry("iron", 1, false)));
        index.clear();
        assertTrue(index.isEmpty());
        assertTrue(refresh(index, repo, 1, List.of(new Entry("iron", 1, false))), "閉じて開き直したら、すぐ作り直す");
    }

    /**
     * 規模の見当(計測の補助。合否は緩い上限だけ): 品 2 万種の写しを作り直す時間。作り直しは 0.5 秒に 1 回までなので、
     * これが数ミリ秒なら描画の糸の負荷は 1% を大きく下回る。
     */
    @Test
    void rebuildOfALargeNetworkIsCheap() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            entries.add(new Entry("item" + i, i, i % 7 == 0));
        }
        StockIndex<String> index = new StockIndex<>(0);
        for (int warm = 0; warm < 20; warm++) {
            refresh(index, new Object(), warm, entries);
        }
        int rounds = 50;
        long start = System.nanoTime();
        for (int r = 0; r < rounds; r++) {
            refresh(index, new Object(), 100 + r, entries);
        }
        double perRebuildMs = (System.nanoTime() - start) / 1e6 / rounds;
        System.out.printf("StockIndex: 20000 entries, %.2f ms per rebuild%n", perRebuildMs);
        assertTrue(perRebuildMs < 50, "作り直しが遅すぎる: " + perRebuildMs + " ms");
    }
}
