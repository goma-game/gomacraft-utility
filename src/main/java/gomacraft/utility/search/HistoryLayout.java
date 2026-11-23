package gomacraft.utility.search;

/**
 * 検索語の履歴のウィンドウを出す位置と大きさ(Minecraft に依らない —— 試験はここを見る。goma-org/gomabase#2295 の
 * 2 回目の確かめの 1・2)。
 *
 * <ul>
 *   <li>{@link Mode#TERMINAL}(AE2 の端末): 検索欄の<b>左</b>、「ターミナル」の見出しの所に重ねる。小さい形は見出しに
 *       収まる 1 行だけ(アイテムの欄を隠さない)。カーソルを合わせると、同じ右端・同じ上端のまま左と下へ広げる(アイテムの
 *       欄に重なってよい)。左は端末の左の道具の列までで止める(その外は EMI の欄で、押したものを EMI が先に取る)。
 *       端末の大きさ(上の空き)に依らないので、最大の大きさでも触れる。左の空きが狭い画面(形の違う端末)は
 *       {@link Mode#AROUND} に倒す。</li>
 *   <li>{@link Mode#EMI}(EMI の検索欄): 欄の左に、欄の下端に揃えて上へ積む(EMI の欄は画面の下にある)。</li>
 *   <li>{@link Mode#AROUND}(コンパスなど): 欄の上。上が狭いときは、端末なら左の外、端末でない画面なら欄の下。</li>
 * </ul>
 */
public final class HistoryLayout {
    public static final int ROW = 11;
    public static final int PAD = 2;
    /** 欄との間。 */
    public static final int GAP = 2;
    public static final int MAX_WIDTH = 220;
    /** 端末の左の外に空ける幅(AE2 の端末の左の道具の列の分)。 */
    public static final int SIDE_GAP = 24;
    /** 欄の横に出すのに要る幅(これより狭ければ、欄の上か下に出す)。 */
    public static final int MIN_SIDE_WIDTH = 60;
    /** 端末の見出しの左の余白(小さい形は、ここから欄の左までを埋めて見出しの字を隠す)。 */
    static final int TITLE_INSET = 3;
    /** 小さい形で見出しを埋めるのに要る幅(これより狭ければ、広げた形の左の端から埋める)。 */
    static final int MIN_TITLE_WIDTH = 40;

    public enum Mode { AROUND, TERMINAL, EMI }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean intersects(Rect o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }

        public boolean contains(Rect o) {
            return o.x >= x && o.y >= y && o.right() <= right() && o.bottom() <= bottom();
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    /**
     * @param field        検索欄の見た目の枠
     * @param panelLeft    端末の左の端(端末でなければ 0)
     * @param container    端末(品の欄を持つ画面)か
     * @param wanted       出したい行の数
     * @param contentWidth 行と案内の字の幅(余白を含む)
     * @param hintLines    案内の行の数(広げた形だけ。0 なら出さない)
     * @param wide         広げた形か
     */
    public record Input(Mode mode, int screenWidth, int screenHeight, Rect field, int panelLeft, boolean container,
                        int wanted, int contentWidth, int hintLines, boolean wide) {
    }

    /**
     * @param box     枠
     * @param rowsTop 行の欄の上端
     * @param rows    出す行の数
     * @param upward  先頭(欄に近い・新しい)を一番下に置くか
     * @param hintTop 案内の行の上端
     */
    public record Placement(Rect box, int rowsTop, int rows, boolean upward, int hintTop) {
    }

    private HistoryLayout() {
    }

    public static Placement place(Input in) {
        return switch (in.mode()) {
            case TERMINAL -> terminal(in);
            case EMI -> emi(in);
            case AROUND -> around(in);
        };
    }

    private static int hintHeight(Input in) {
        return in.hintLines() * ROW;
    }

    private static Placement terminal(Input in) {
        Rect field = in.field();
        int right = field.x() - GAP;
        int limit = Math.max(0, in.panelLeft() - SIDE_GAP);
        if (right - limit < MIN_SIDE_WIDTH) {
            return around(in);
        }
        int titleLeft = in.panelLeft() + TITLE_INSET;
        int compactLeft = right - titleLeft >= MIN_TITLE_WIDTH ? titleLeft : limit;
        int compactWidth = right - compactLeft;
        int width = in.wide()
                ? Math.max(compactWidth, Math.min(Math.min(in.contentWidth(), MAX_WIDTH), right - limit))
                : compactWidth;
        // 1 行の枠(高さ 15)を、欄の見た目の枠(高さ 12)と上下でほぼ揃える(見出しの下の端を越えない)
        int top = Math.max(0, field.y() - 2);
        // 小さい形は見出しに収まる 1 行だけ。広げたら、画面の下の端まで
        int count = in.wide() ? in.wanted() : 1;
        int room = (in.screenHeight() - top - PAD * 2 - (in.wide() ? hintHeight(in) : 0)) / ROW;
        int rows = Math.max(1, Math.min(count, room));
        int hint = in.wide() ? hintHeight(in) : 0;
        int height = rows * ROW + PAD * 2 + hint;
        int rowsTop = top + PAD;
        return new Placement(new Rect(right - width, top, width, height), rowsTop, rows, false, rowsTop + rows * ROW);
    }

    private static Placement emi(Input in) {
        Rect field = in.field();
        int right = field.x() - GAP;
        if (right < MIN_SIDE_WIDTH) {
            return around(in);
        }
        int bottom = Math.min(in.screenHeight(), field.bottom());
        int hint = in.wide() ? hintHeight(in) : 0;
        int room = (bottom - PAD * 2 - hint) / ROW;
        int rows = Math.max(1, Math.min(in.wanted(), room));
        int width = Math.max(MIN_SIDE_WIDTH, Math.min(Math.min(in.contentWidth(), MAX_WIDTH), right));
        int height = rows * ROW + PAD * 2 + hint;
        int top = Math.max(0, bottom - height);
        int hintTop = top + PAD;
        return new Placement(new Rect(right - width, top, width, height), hintTop + hint, rows, true, hintTop);
    }

    /** 前からの置き方(欄の上。狭ければ端末は左の外、端末でない画面は欄の下)。 */
    private static Placement around(Input in) {
        Rect field = in.field();
        int count = in.wanted();
        int hint = in.wide() ? hintHeight(in) : 0;
        int room = (field.y() - 1 - PAD * 2 - hint) / ROW;
        boolean container = in.container();
        int leftEdge = container ? in.panelLeft() - SIDE_GAP : 0;
        int belowTop = field.bottom() + 1;
        int belowRoom = (in.screenHeight() - belowTop - PAD * 2 - hint) / ROW;
        // 端末でない画面は、上が狭くても、下の空きの方が狭ければ上に出す(#21 のレビュー)
        boolean upward = room >= Math.min(count, 3) || (container && leftEdge < MIN_SIDE_WIDTH)
                || (!container && belowRoom <= room);
        int rows;
        if (upward) {
            rows = Math.max(1, Math.min(count, room));
        } else {
            int start = container ? field.y() : belowTop;
            int below = (in.screenHeight() - start - PAD * 2 - hint) / ROW;
            rows = Math.max(1, Math.min(count, below));
        }
        int width = Math.min(Math.max(field.width(), in.contentWidth()), Math.max(field.width(), MAX_WIDTH));
        int height = rows * ROW + PAD * 2 + hint;
        int x;
        int top;
        if (upward) {
            x = Math.max(0, Math.min(field.x(), in.screenWidth() - width - 1));
            top = Math.max(0, field.y() - 1 - height);
        } else if (container) {
            width = Math.min(width, leftEdge - 1);
            x = leftEdge - width;
            top = field.y();
        } else {
            x = Math.max(0, Math.min(field.x(), in.screenWidth() - width - 1));
            top = belowTop;
        }
        int hintTop = top + PAD;
        return new Placement(new Rect(x, top, width, height), hintTop + hint, rows, upward, hintTop);
    }
}
