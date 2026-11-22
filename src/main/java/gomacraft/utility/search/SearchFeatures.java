package gomacraft.utility.search;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.Requirement;
import gomacraft.utility.feature.Setting;
import java.util.List;

/**
 * 段 1(検索まわり)の機能の一覧(goma-org/gomabase#2295)。どれもクライアントだけで動く。
 *
 * <p>このクラスはサーバでも読まれうるので、クライアントのクラスを名前で持たない。中身は
 * {@code gomacraft.utility.search.client} に置き、{@link Feature#init} の中から呼ぶ(呼ばれるのはクライアントだけ)。
 */
public final class SearchFeatures {
    /** AE2 の端末(パターン端末を含む)の名前の検索で、英語名でも当たるようにする。 */
    public static final String AE2_ENGLISH_SEARCH = "ae2_english_search";
    /** マウスの下の品の名前を、キー(既定は F)で AE2 の端末の検索欄へ送る。 */
    public static final String AE2_SEARCH_KEY = "ae2_search_key";
    /** AE2 の端末の検索語の履歴(検索欄の下に一覧を出す)。 */
    public static final String AE2_SEARCH_HISTORY = "ae2_search_history";
    /** Nature's Compass の検索で、英語名でも当たるようにする。 */
    public static final String NATURESCOMPASS_SEARCH = "naturescompass_search";
    /** Explorer's Compass の検索で、英語名でも当たるようにする。 */
    public static final String EXPLORERSCOMPASS_SEARCH = "explorerscompass_search";
    /** mcjtylib の画面(RFTools 系・XNet など)の、英語で直に書かれた文言に訳を当てる差し込み口。 */
    public static final String MCJTYLIB_TEXT = "mcjtylib_text";

    /** AE2 の版の範囲。当て先が変わったときは、版ではなく mixin の当て損ね(FAILED)で気付く。 */
    static final String AE2_RANGE = "[19.2,20)";

    private SearchFeatures() {
    }

    /** どれも画面の機能({@link Feature#clientOnly})。専用サーバでは {@code off(client only)} になる。 */
    public static List<Feature> all() {
        return List.of(
                feature(AE2_ENGLISH_SEARCH, List.of(Requirement.of("ae2", AE2_RANGE)),
                        context -> gomacraft.utility.search.client.EnglishNames.install(context, AE2_ENGLISH_SEARCH)),
                feature(AE2_SEARCH_KEY, List.of(Requirement.of("ae2", AE2_RANGE)),
                        context -> gomacraft.utility.search.client.Ae2SearchKey.init(context)),
                feature(AE2_SEARCH_HISTORY, List.of(Requirement.of("ae2", AE2_RANGE)), HISTORY_SETTINGS,
                        context -> gomacraft.utility.search.client.Ae2SearchHistory.init(context)),
                // コンパスと mcjtylib の版は Maven の形でないので、範囲を書かない(当て先が変われば FAILED で気付く)
                feature(NATURESCOMPASS_SEARCH, List.of(Requirement.of("naturescompass")),
                        context -> gomacraft.utility.search.client.EnglishNames.install(context, NATURESCOMPASS_SEARCH)),
                feature(EXPLORERSCOMPASS_SEARCH, List.of(Requirement.of("explorerscompass")),
                        context -> gomacraft.utility.search.client.EnglishNames.install(context, EXPLORERSCOMPASS_SEARCH)),
                feature(MCJTYLIB_TEXT, List.of(Requirement.of("mcjtylib")), context -> {
                }));
    }

    @FunctionalInterface
    interface Init {
        void run(FeatureContext context) throws Exception;
    }

    /** 検索語の履歴: 欄が空のときに出す件数(ホバーしていないとき)。 */
    public static final Setting HISTORY_COLLAPSED = Setting.ofInt("ae2_search_history_collapsed", 3, 1, 20,
            "検索語の履歴: 欄が空でホバーしていないときに出す件数");
    /** 検索語の履歴: 広げたとき(ホバー・入力中)に一度に出す件数。それより古いものはスクロールで出す。 */
    public static final Setting HISTORY_VISIBLE = Setting.ofInt("ae2_search_history_visible", 10, 1, 30,
            "検索語の履歴: 広げたとき(ホバー・入力中)に一度に出す件数。古いものはスクロールで遡る");
    /** 検索語の履歴: 持つ件数(お気に入りは数えない)。 */
    public static final Setting HISTORY_KEEP = Setting.ofInt("ae2_search_history_keep", 100, 10, 1000,
            "検索語の履歴: 持つ件数(お気に入りは数えない。古いものから消す)");
    static final List<Setting> HISTORY_SETTINGS = List.of(HISTORY_COLLAPSED, HISTORY_VISIBLE, HISTORY_KEEP);

    private static Feature feature(String id, List<Requirement> requirements, Init init) {
        return feature(id, requirements, List.of(), init);
    }

    private static Feature feature(String id, List<Requirement> requirements, List<Setting> settings, Init init) {
        return new Feature() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<Requirement> requirements() {
                return requirements;
            }

            @Override
            public List<Setting> settings() {
                return settings;
            }

            @Override
            public boolean clientOnly() {
                return true;
            }

            @Override
            public void init(FeatureContext context) throws Exception {
                init.run(context);
            }
        };
    }
}
