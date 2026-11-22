package gomacraft.utility.search;

import gomacraft.utility.settings.ClientSettings;

/**
 * 検索語の履歴の件数の設定(AE2 の端末とコンパスで共通)。⚠ AE2 のクラスを名前で持たない(AE2 の無い組でもコンパスの
 * 履歴がこの class を読む —— #21 のレビュー)。設定の画面では AE2 の端末の履歴の機能の欄に出る(前からの置き場)。
 */
public final class SearchHistorySettings {
    /** 欄が空でホバーしていないときに出す件数(設定の画面で変えられる。すぐ効く)。 */
    public static final ClientSettings.Int COLLAPSED_ROWS =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "collapsed_rows", 3, 1, 20);
    /** 広げたとき(ホバー・入力中)に一度に出す件数。それより古いものはホイールで遡る。 */
    public static final ClientSettings.Int VISIBLE_ROWS =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "visible_rows", 10, 1, 30);
    /** 持つ件数(お気に入りは数えない。古いものから消す)。 */
    public static final ClientSettings.Int KEEP =
            new ClientSettings.Int(SearchFeatures.AE2_SEARCH_HISTORY, "keep", 100, 10, 1000);

    private SearchHistorySettings() {
    }
}
