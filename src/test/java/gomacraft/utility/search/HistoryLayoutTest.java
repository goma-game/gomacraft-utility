package gomacraft.utility.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import gomacraft.utility.search.HistoryLayout.Input;
import gomacraft.utility.search.HistoryLayout.Mode;
import gomacraft.utility.search.HistoryLayout.Placement;
import gomacraft.utility.search.HistoryLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 検索語の履歴のウィンドウが、どの画面の大きさ・GUI の大きさ・ターミナルの大きさでも触れる所に出ること
 * (goma-org/gomabase#2295 の 2 回目の確かめの 1 —— 最大の大きさのターミナルで左へ逃げて触れなくなった)。
 * ターミナルの形は AE2 の {@code base_terminal.json}(検索欄 left 80・top 4・幅 89・高さ 12、見出しの高さ 17、
 * 行 18、下 99)。
 */
class HistoryLayoutTest {
    private static final int[][] MONITORS = {{1280, 720}, {1366, 768}, {1920, 1080}, {2560, 1440}, {3840, 2160}};
    private static final int TERMINAL_WIDTH = 195;
    private static final int HEADER = 17;

    private record Case(String name, int screenW, int screenH, Rect field, int guiLeft, int guiTop) {
    }

    /** GUI の大きさ 1〜6 と、ターミナルの行の数(3 行から画面に収まる最大まで)の組。 */
    private static List<Case> terminals() {
        List<Case> cases = new ArrayList<>();
        for (int[] m : MONITORS) {
            for (int scale = 1; scale <= 6; scale++) {
                int w = (int) Math.ceil(m[0] / (double) scale);
                int h = (int) Math.ceil(m[1] / (double) scale);
                for (int rows = 3; HEADER + rows * 18 + 99 <= h; rows++) {
                    int height = HEADER + rows * 18 + 99;
                    int guiLeft = (w - TERMINAL_WIDTH) / 2;
                    int guiTop = (h - height) / 2;
                    if (guiLeft < 0) {
                        continue;
                    }
                    cases.add(new Case(m[0] + "x" + m[1] + "@" + scale + " rows=" + rows, w, h,
                            new Rect(guiLeft + 80, guiTop + 4, 89, 12), guiLeft, guiTop));
                }
            }
        }
        return cases;
    }

    private static Placement place(Mode mode, Case c, boolean wide) {
        return HistoryLayout.place(new Input(mode, c.screenW(), c.screenH(), c.field(), c.guiLeft(),
                mode == Mode.TERMINAL, wide ? 10 : 3, wide ? 200 : 80, wide ? 3 : 0, wide));
    }

    /** 画面の中・欄と重ならない・欄の隣(欄から横へ動かせば入る)。 */
    private static void checkReachable(String where, Case c, Rect box, List<String> problems) {
        if (box.x() < 0 || box.y() < 0 || box.right() > c.screenW() || box.bottom() > c.screenH()) {
            problems.add(where + ": 画面の外 " + box);
        }
        Rect field = c.field();
        if (box.intersects(field)) {
            problems.add(where + ": 欄に重なる " + box);
        }
        // 左の隣(縦に重なる)か、狭い画面で上か下へ倒したときは上下の隣(横に重なる)。間は 3 まで
        boolean left = box.right() >= field.x() - 3 && box.right() <= field.x()
                && box.y() < field.bottom() && box.bottom() > field.y();
        boolean vertical = box.x() < field.right() && box.right() > field.x()
                && ((box.bottom() <= field.y() && box.bottom() >= field.y() - 3)
                        || (box.y() >= field.bottom() && box.y() <= field.bottom() + 3));
        if (!left && !vertical) {
            problems.add(where + ": 欄の隣でない " + box + " field=" + field);
        }
    }

    @Test
    void terminalWindowIsBesideTheFieldAtEverySize() {
        List<String> problems = new ArrayList<>();
        List<Case> cases = terminals();
        assertTrue(cases.size() > 100, "組が少ない: " + cases.size());
        for (Case c : cases) {
            Placement compact = place(Mode.TERMINAL, c, false);
            Placement wide = place(Mode.TERMINAL, c, true);
            checkReachable(c.name() + " compact", c, compact.box(), problems);
            checkReachable(c.name() + " wide", c, wide.box(), problems);
            // 小さい形は見出しの中(アイテムの欄を隠さない)。広げた形は小さい形を含む(合わせたカーソルが外れない)
            if (compact.box().bottom() > c.guiTop() + HEADER || compact.box().x() < c.guiLeft()) {
                problems.add(c.name() + ": 小さい形が見出しの外 " + compact.box());
            }
            if (!wide.box().contains(compact.box())) {
                problems.add(c.name() + ": 広げた形が小さい形を含まない " + wide.box() + " / " + compact.box());
            }
            // 広げた形は、端末の左の道具の列より外(EMI の欄)へ出ない
            if (wide.box().x() < c.guiLeft() - HistoryLayout.SIDE_GAP) {
                problems.add(c.name() + ": 広げた形が EMI の欄へはみ出す " + wide.box());
            }
            if (wide.rows() < 3) {
                problems.add(c.name() + ": 広げても行が少ない " + wide.rows());
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems.subList(0, Math.min(20, problems.size()))));
    }

    @Test
    void emiWindowIsBesideTheSearchBoxAtEverySize() {
        List<String> problems = new ArrayList<>();
        for (int[] m : MONITORS) {
            for (int scale = 1; scale <= 6; scale++) {
                int w = (int) Math.ceil(m[0] / (double) scale);
                int h = (int) Math.ceil(m[1] / (double) scale);
                // EMI の検索欄は画面の下の中央(幅 160、高さ 18)
                Rect field = new Rect(w / 2 - 80, h - 22, 160, 18);
                Case c = new Case(m[0] + "x" + m[1] + "@" + scale, w, h, field, 0, 0);
                for (boolean wide : new boolean[] {false, true}) {
                    Placement p = place(Mode.EMI, c, wide);
                    checkReachable(c.name() + (wide ? " wide" : " compact"), c, p.box(), problems);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems.subList(0, Math.min(20, problems.size()))));
    }
}
