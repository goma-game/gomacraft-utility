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
        // コンパスの右の空きの終わりは、右上のボタン(画面の右から 120)の手前
        return HistoryLayout.place(new Input(mode, c.screenW(), c.screenH(), c.field(), c.guiLeft(),
                c.screenW() - 122, mode == Mode.TERMINAL, wide ? 10 : 3, wide ? 3 : 0, wide));
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
        boolean right = box.x() <= field.right() + 3 && box.x() >= field.right()
                && box.y() < field.bottom() && box.bottom() > field.y();
        if (!left && !right && !vertical) {
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
    void compassWindowIsBesideTheSearchBoxAtEverySize() {
        List<String> problems = new ArrayList<>();
        for (int[] m : MONITORS) {
            for (int scale = 1; scale <= 6; scale++) {
                int w = (int) Math.ceil(m[0] / (double) scale);
                int h = (int) Math.ceil(m[1] / (double) scale);
                // Nature's Compass(欄 x 130)と Explorer's Compass(欄 x 幅/2 - 82)。どちらも y 10・幅 140・高さ 20、
                // 結果の一覧は y 40 から
                for (Rect field : List.of(new Rect(130, 10, 140, 20), new Rect(w / 2 - 82, 10, 140, 20))) {
                    Case c = new Case(m[0] + "x" + m[1] + "@" + scale + " x=" + field.x(), w, h, field, 0, 0);
                    Placement compact = place(Mode.RIGHT, c, false);
                    Placement wide = place(Mode.RIGHT, c, true);
                    checkReachable(c.name() + " compact", c, compact.box(), problems);
                    checkReachable(c.name() + " wide", c, wide.box(), problems);
                    // 小さい形は、結果の一覧(y 40 から)の 1 番目の名前の出る左の方(欄の右の端より左)を隠さない
                    // (欄そのものが画面の右からはみ出す、壊れた大きさは除く)
                    boolean fits = field.right() + HistoryLayout.GAP + 40 <= w - 2;
                    // (一覧の左の端 x 130 が欄の右の端の近くまで来る、ごく狭い画面は名前の所が無いので見ない)
                    int nameWidth = field.right() - 40 - 130;
                    Rect firstName = new Rect(130, 40, Math.max(1, nameWidth), 20);
                    if (fits && nameWidth > 0 && compact.box().intersects(firstName)) {
                        problems.add(c.name() + ": 小さい形が 1 番目の結果の名前を隠す " + compact.box());
                    }
                    // 右上のテレポートのボタン(画面の右から 120・幅 110・y 10〜30)に重ねない(欄に focus があるあいだ
                    // ボタンが効かなくなる)。幅は MAX_WIDTH まで
                    Rect teleport = new Rect(w - 120, 10, 110, 20);
                    for (Placement p : List.of(compact, wide)) {
                        if (p.box().intersects(teleport)) {
                            problems.add(c.name() + ": テレポートのボタンに重なる " + p.box());
                        }
                        if (p.box().width() > HistoryLayout.MAX_WIDTH) {
                            problems.add(c.name() + ": 幅が上限を越える " + p.box());
                        }
                    }
                    if (!wide.box().contains(compact.box())) {
                        problems.add(c.name() + ": 広げた形が小さい形を含まない " + wide.box() + " / " + compact.box());
                    }
                }
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
