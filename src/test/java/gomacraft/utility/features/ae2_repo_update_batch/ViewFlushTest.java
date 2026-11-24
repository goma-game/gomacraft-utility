package gomacraft.utility.features.ae2_repo_update_batch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * 済んでいない並べ直しを済ませるのは描画のスレッドだけ(goma-org/gomabase#2295 の 3 回目の確かめのログ —— EMI の検索の
 * 焼き付けのスレッドが一覧を読んで並べ直し、描画のスレッドの並べ直しと重なって AE2 の通信の処理が落ちた。落ちても
 * 表示が古くなるだけで、何も赤くならない)。
 */
class ViewFlushTest {
    @Test
    void onlyTheRenderThreadFlushes() throws Exception {
        Thread render = Thread.currentThread();
        assertTrue(ViewFlush.mayFlush(true, Thread.currentThread() == render), "描画のスレッドは並べ直す");
        assertFalse(ViewFlush.mayFlush(false, true), "済んでいなければ何もしない");

        // ほかのスレッド(EMI の焼き付けのスレッドの代わり)からの読みでは並べ直さない
        AtomicBoolean flushedElsewhere = new AtomicBoolean(true);
        Thread other = new Thread(() -> flushedElsewhere.set(
                ViewFlush.mayFlush(true, Thread.currentThread() == render)));
        other.start();
        other.join();
        assertFalse(flushedElsewhere.get(), "ほかのスレッドでは並べ直さない(印は残して、描画のスレッドの次の読みで済ませる)");
    }
}
