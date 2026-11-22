package gomacraft.utility.screenshot.server;

import gomacraft.utility.screenshot.ScreenshotRules;
import gomacraft.utility.screenshot.ScreenshotRules.Result;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 受けている途中の送りの帳簿(サーバの主スレッドだけが触る)。Minecraft に依らないので JUnit で確かめる。
 *
 * <p>守り(悪意のクライアントを前提にする): 1 人 1 本・全体で {@link ScreenshotRules#MAX_IN_FLIGHT} 本まで、
 * 1 本を始めたら {@link ScreenshotRules#COOLDOWN_MILLIS} は次を始めない、最初の切れ端で全体の大きさを宣言させて
 * 上限を超えたら断る、順番どおりでない・大きさが合わない切れ端はその送りごと捨てる。
 */
public final class InboxState {
    /** 切れ端を受けた結果。{@code result} が空でなければ返事を出す。{@code done} は揃った 1 枚。 */
    public record Step(Result result, byte[] done) {
        static Step reply(Result result) {
            return new Step(result, null);
        }

        static Step quiet() {
            return new Step(null, null);
        }
    }

    private static final class Upload {
        final int id;
        final int total;
        final byte[] data;
        final long started;
        int next;

        Upload(int id, int total, long started) {
            this.id = id;
            this.total = total;
            this.data = new byte[total];
            this.started = started;
        }
    }

    private final Map<UUID, Upload> uploads = new HashMap<>();
    private final Map<UUID, Long> lastStart = new HashMap<>();
    /** 揃って、置き場へ書いている途中の数(書き終えたら {@link #written} で減らす)。 */
    private int writing;

    /**
     * 1 個の切れ端を受ける。
     *
     * @param spoolFull 置き場が上限に達しているか(最初の切れ端でだけ見る)
     */
    public Step accept(UUID player, int upload, int total, int index, byte[] data, long now, boolean spoolFull) {
        Upload current = uploads.get(player);
        if (index == 0) {
            if (current != null) {
                return Step.reply(Result.BUSY);
            }
            Long last = lastStart.get(player);
            if (last != null && now - last < ScreenshotRules.COOLDOWN_MILLIS) {
                return Step.reply(Result.COOLDOWN);
            }
            if (uploads.size() + writing >= ScreenshotRules.MAX_IN_FLIGHT) {
                return Step.reply(Result.BUSY);
            }
            if (!ScreenshotRules.acceptableTotal(total)) {
                return Step.reply(Result.TOO_LARGE);
            }
            if (ScreenshotRules.sniff(data).isEmpty()) {
                return Step.reply(Result.BAD_IMAGE);
            }
            if (spoolFull) {
                return Step.reply(Result.SPOOL_FULL);
            }
            current = new Upload(upload, total, now);
            uploads.put(player, current);
            lastStart.put(player, now);
        } else if (current == null || current.id != upload) {
            // 捨てた送りの残り・古い送りの切れ端(返事は出さない —— 同じ理由を何度も返さない)
            return Step.quiet();
        }
        if (total != current.total || index != current.next
                || data.length != ScreenshotRules.chunkLength(current.total, index)) {
            uploads.remove(player);
            return Step.reply(Result.BROKEN);
        }
        System.arraycopy(data, 0, current.data, index * ScreenshotRules.CHUNK_BYTES, data.length);
        current.next++;
        if (current.next < ScreenshotRules.chunkCount(current.total)) {
            return Step.quiet();
        }
        uploads.remove(player);
        writing++;
        return new Step(null, current.data);
    }

    /** 揃った 1 枚を書き終えた(成否によらず呼ぶ)。 */
    public void written() {
        if (writing > 0) {
            writing--;
        }
    }

    /** 揃わないまま {@link ScreenshotRules#TIMEOUT_MILLIS} を過ぎた送りを捨てる。捨てた数を返す。 */
    public int prune(long now) {
        int before = uploads.size();
        uploads.values().removeIf(u -> now - u.started > ScreenshotRules.TIMEOUT_MILLIS);
        lastStart.values().removeIf(t -> now - t > ScreenshotRules.COOLDOWN_MILLIS);
        return before - uploads.size();
    }

    /** 抜けた人の途中の送りを捨てる。 */
    public void forget(UUID player) {
        uploads.remove(player);
    }

    int inFlight() {
        return uploads.size() + writing;
    }
}
