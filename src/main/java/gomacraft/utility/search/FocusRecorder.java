package gomacraft.utility.search;

/**
 * 検索欄の語を履歴に記す契機を決める(Minecraft に依らない —— 試験はここを見る。goma-org/gomabase#2295 の 3 回目の
 * 確かめの 1)。履歴のウィンドウ({@code SearchHistoryOverlay})が、欄ごとに 1 つ持つ。
 *
 * <p>契機は 2 つだけ。どちらも、押した・打った出来事ではなく<b>欄の様子</b>で決める。
 * <ul>
 *   <li>呼ぶ側が直に記す道(Enter・履歴の語を使う・欄の外を押す)もあり、記したら {@link #recorded} を呼ぶ。⚠ 欄の外を
 *       押しても focus を外さない画面がある(Nature's Compass)ので、押した道は残す。</li>
 *   <li><b>focus が外れたとき</b>(tick ごとに {@link #observe} で見る): Enter・欄の外を押す・Tab・Esc で
 *       外れるのを、どの道で外れたかに依らずに拾う。⚠ 前は「欄の外を押した」を押した出来事で見ていて、履歴のウィンドウが
 *       出ていないとき(打った語に合う履歴が無いとき —— 新しい語はいつもそう)は記さなかった。EMI は押すのを画面の
 *       出来事より先に自分で受けるので、出来事の順にも依りうる。</li>
 *   <li><b>画面を閉じたとき</b>({@link #close}): focus があれば記す。focus が無くても記すかは欄ごと
 *       (AE2 のターミナルは記す —— 閉じるまで欄に語が残るのが検索の結果。EMI の欄は画面をまたいで語が残るので記さない)。</li>
 * </ul>
 * 記すのは、その時点の欄の値(履歴の語を使ったときは、使った語)。
 */
public final class FocusRecorder {
    private boolean focused;

    /**
     * 欄の今の様子を渡す。focus が外れたら、記す語を返す(無ければ null)。
     *
     * @param present 欄が今の画面に在って見えるか
     */
    public String observe(boolean present, boolean focusedNow, String value) {
        if (!present) {
            // 画面が変わった(閉じたときは close が記す)。前の focus は持ち越さない
            focused = false;
            return null;
        }
        boolean was = focused;
        focused = focusedNow;
        return was && !focusedNow ? value : null;
    }

    /**
     * 呼ぶ側が直に記した(Enter・履歴の語を使った・欄の外を押した)。次の {@link #observe} で focus が外れたのを見ても、
     * 同じ語を 2 度記さない(ファイルを 2 度書くと、書けないときに失敗を 2 回数えて FAILED に早く届く)。
     */
    public void recorded() {
        focused = false;
    }

    /**
     * 画面を閉じたときに渡す。記す語を返す(無ければ null)。
     *
     * @param recordUnfocused focus が無くても記すか
     */
    public String close(boolean present, boolean focusedNow, String value, boolean recordUnfocused) {
        boolean was = focused;
        focused = false;
        if (!present) {
            return null;
        }
        return focusedNow || was || recordUnfocused ? value : null;
    }
}
