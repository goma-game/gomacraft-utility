package gomacraft.utility.emiae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteStockCacheTest {
    private static RemoteStockCache<String> cache() {
        return new RemoteStockCache<>(3000, 1000, 10000, 2);
    }

    @Test
    void asksOnlyForSeenKeysAtMostOncePerIntervalAndWithinTheLimit() {
        RemoteStockCache<String> c = cache();
        assertNull(c.get("a", 0));
        assertNull(c.get("b", 0));
        assertNull(c.get("c", 0));
        assertEquals(List.of("a", "b"), c.takeDue(0), "1 回は上限まで");
        assertEquals(List.of(), c.takeDue(999), "間隔の内は送らない");
        assertEquals(List.of("c"), c.takeDue(1000));
        assertEquals(List.of(), c.takeDue(5000), "見ていない品は問わない");
    }

    @Test
    void keepsShowingStaleCountsWhileAskingAgain() {
        RemoteStockCache<String> c = cache();
        c.get("a", 0);
        c.takeDue(0);
        c.put("a", new StockIndex.Stock(5, true), 100);
        assertEquals(5, c.get("a", 200).stored());
        assertEquals(List.of(), c.takeDue(1200), "新しい答えは問い直さない");
        assertEquals(5, c.get("a", 3100).stored(), "古くなっても数は出し続ける(点滅させない)");
        assertEquals(List.of("a"), c.takeDue(3100), "古くなったら問い直す");
    }

    @Test
    void zeroAndNotCraftableIsNotShown() {
        RemoteStockCache<String> c = cache();
        c.put("a", new StockIndex.Stock(0, false), 0);
        assertNull(c.get("a", 1));
    }

    @Test
    void unreachableClearsAndBacksOff() {
        RemoteStockCache<String> c = cache();
        c.put("a", new StockIndex.Stock(5, false), 0);
        c.unreachable(100);
        assertNull(c.get("a", 200));
        assertEquals(List.of(), c.takeDue(5000), "届かないと言われたら、しばらく問わない");
        assertEquals(List.of("a"), c.takeDue(10100));
        c.clear();
        assertTrue(c.takeDue(10200).isEmpty());
    }
}
