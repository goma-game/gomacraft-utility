package gomacraft.utility.emiae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteStockCacheTest {
    /** 寿命 3 秒・間隔 1 秒・届かないときの間 10 秒・返事の期限 2.5 秒・1 回 2 品。 */
    private static RemoteStockCache<String> cache() {
        return new RemoteStockCache<>(3000, 1000, 10000, 2500, 2);
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
    void doesNotAskAgainWhileWaitingForTheReply() {
        RemoteStockCache<String> c = cache();
        c.get("a", 0);
        assertEquals(List.of("a"), c.takeDue(0));
        // 返事の前に描画が何度も同じ品を求めても、溜めない
        c.get("a", 100);
        c.get("a", 1100);
        assertEquals(List.of(), c.takeDue(1100), "返事を待っている品をもう一度送った");
        c.put("a", new StockIndex.Stock(5, false), 1200);
        assertEquals(5, c.get("a", 1300).stored());
    }

    @Test
    void aDroppedQueryIsAskedAgainAfterTheReplyTimeout() {
        RemoteStockCache<String> c = cache();
        c.get("a", 0);
        c.takeDue(0);
        // サーバが黙って捨てた(返事が来ない)。期限の後は、また問う
        c.get("a", 2499);
        assertEquals(List.of(), c.takeDue(2499));
        c.get("a", 2500);
        assertEquals(List.of("a"), c.takeDue(2500));
    }

    @Test
    void sendsEachKeyOncePerCycleOverTime() {
        // 30 秒、毎フレーム(16 ミリ秒ごと)同じ品を見て、返事は送ってから 200 ミリ秒で来る
        RemoteStockCache<String> c = new RemoteStockCache<>(5000, 2000, 10000, 5000, 256);
        List<Long> sentAt = new ArrayList<>();
        long replyAt = -1;
        for (long now = 0; now <= 30000; now += 16) {
            c.get("a", now);
            if (!c.takeDue(now).isEmpty()) {
                sentAt.add(now);
                replyAt = now + 200;
            }
            if (replyAt >= 0 && now >= replyAt) {
                c.put("a", new StockIndex.Stock(1, false), now);
                replyAt = -1;
            }
        }
        // 寿命 5 秒ごとに 1 回(0, 5.2, 10.4 … 秒あたり)で、30 秒で 6 回
        assertEquals(6, sentAt.size(), "送った時刻: " + sentAt);
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
    void stopsShowingCountsTooOldWhenTheServerStopsAnswering() {
        RemoteStockCache<String> c = cache();
        c.put("a", new StockIndex.Stock(5, false), 0);
        assertEquals(5, c.get("a", 8999).stored(), "寿命の 3 倍の内は出す");
        assertNull(c.get("a", 9000), "寿命の 3 倍を過ぎた数は出さない");
    }

    @Test
    void putBackReturnsKeysThatDidNotFitToTheQueue() {
        RemoteStockCache<String> c = cache();
        c.get("a", 0);
        c.get("b", 0);
        List<String> taken = c.takeDue(0);
        assertEquals(List.of("a", "b"), taken);
        c.putBack(List.of("b"));
        assertEquals(List.of("b"), c.takeDue(1000), "戻した品は次の回に送る(返事の待ちに残らない)");
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
