package gomacraft.utility.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 検索欄の語を履歴に記す契機(goma-org/gomabase#2295 の 3 回目の確かめの 1 —— AE2 のターミナルの語が記されなくなり、
 * 何も赤くならないまま 1 版を出してしまった)。AE2 のターミナル(閉じたら focus が無くても記す)と EMI の欄
 * (記さない)の両方で、Enter・欄の外を押す・閉じるの道を見る。
 */
class FocusRecorderTest {
    @Test
    void recordsWhenFocusIsLostOrTheScreenCloses() {
        for (boolean terminal : new boolean[] {true, false}) {
            String who = terminal ? "AE2 のターミナル: " : "EMI の欄: ";

            // 打って、Enter か欄の外を押して focus が外れた(履歴のウィンドウが出ていなくても —— 新しい語)
            FocusRecorder r = new FocusRecorder();
            assertNull(r.observe(true, false, ""), who + "focus が無いあいだは記さない");
            assertNull(r.observe(true, true, "i"), who + "打っているあいだは記さない");
            assertNull(r.observe(true, true, "iron"), who + "打っているあいだは記さない");
            assertEquals("iron", r.observe(true, false, "iron"), who + "focus が外れたら記す");
            assertNull(r.observe(true, false, "iron"), who + "外れたままなら 2 度は記さない");

            // 直に記した(Enter・履歴の語を使った)後は、focus が外れても 2 度は記さない
            r = new FocusRecorder();
            r.observe(true, true, "lead");
            r.recorded();
            assertNull(r.observe(true, false, "lead"), who + "直に記した語を 2 度記さない");

            // focus があるまま閉じた
            r = new FocusRecorder();
            r.observe(true, true, "gold");
            assertEquals("gold", r.close(true, true, "gold", terminal), who + "focus があるまま閉じたら記す");
            assertNull(r.observe(false, false, ""), who + "閉じた後は記さない");

            // focus の無いまま閉じた(ターミナルは閉じるまで残る語が検索の結果なので記す。EMI の欄は記さない)
            r = new FocusRecorder();
            String closed = r.close(true, false, "copper", terminal);
            assertEquals(terminal ? "copper" : null, closed, who + "focus の無いまま閉じたとき");

            // 別の画面へ移った(欄が無い)ときは、前の focus を持ち越さない
            r = new FocusRecorder();
            r.observe(true, true, "tin");
            assertNull(r.observe(false, false, ""), who + "欄の無い画面では記さない");
            assertNull(r.observe(true, false, "tin"), who + "戻ってきても、前の focus では記さない");

            // reset() 後は直前の focus を持ち越さない
            r = new FocusRecorder();
            r.observe(true, true, "silver");
            r.reset();
            assertNull(r.observe(true, false, "silver"), who + "reset 後は前の focus を持ち越さない");
        }
    }
}
