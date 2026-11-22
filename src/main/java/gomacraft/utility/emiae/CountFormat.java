package gomacraft.utility.emiae;

/** 一覧の小さな枠に入る、短い数の書き方(999 / 1.2k / 12k / 3.4M …)。 */
public final class CountFormat {
    private static final String[] UNITS = {"k", "M", "G", "T", "P", "E"};

    private CountFormat() {
    }

    public static String shortCount(long n) {
        if (n < 1000) {
            return Long.toString(Math.max(0, n));
        }
        double value = n;
        int unit = -1;
        while (value >= 1000 && unit < UNITS.length - 1) {
            value /= 1000;
            unit++;
        }
        if (value < 10) {
            // 切り捨てで 1 桁の小数(1999 を 2.0k と丸めて多く見せない)
            long tenths = (long) Math.floor(value * 10);
            return tenths % 10 == 0 ? (tenths / 10) + UNITS[unit] : (tenths / 10) + "." + (tenths % 10) + UNITS[unit];
        }
        return (long) Math.floor(value) + UNITS[unit];
    }
}
