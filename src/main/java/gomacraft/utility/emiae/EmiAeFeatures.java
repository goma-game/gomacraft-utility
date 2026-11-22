package gomacraft.utility.emiae;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.Requirement;
import java.util.List;
import java.util.function.Consumer;
import net.neoforged.bus.api.IEventBus;

/**
 * 段 2(EMI と AE2)の機能(goma-org/gomabase#2295)。
 *
 * <p>⚠ どれも、AE2 のネットワークの中身を EMI の「手持ち」({@code EmiPlayerInventory})へ流さない。流すと EMI が
 * ネットワークの中身の変わるたびに「作れるレシピ」を作り直し、端末が重くなる(EMI Link を外した理由 ——
 * goma-org/gomabase#2286)。在庫の数は、この Mod が持つ索引から描画のときに読むだけにする。
 */
public final class EmiAeFeatures {
    /** EMI の一覧に、AE のネットワークの在庫数と「クラフト可」の印を出す(閉じている間は {@link #STOCK_QUERY} で問う)。 */
    public static final String STOCK = "emi_ae_stock";
    /** AE2 のクラフト計画の画面(CraftConfirmScreen)でも EMI の一覧を出す。 */
    public static final String CRAFT_CONFIRM = "emi_ae_craft_confirm";
    /** EMI の一覧の品を、1 回の操作で手持ちへ(在れば取り出し、無ければ AE2 の自動クラフトを始める)。 */
    public static final String QUICK_CRAFT = "emi_ae_quick_craft";

    /**
     * 端末を閉じているときの在庫数の問い合わせ(サーバが答え、画面が問う)。サーバは AE2 だけが要る。
     * ⚠ 答えるのは、その人のワイヤレスの端末が今つながるネットワークだけ(範囲の外では答えない)。
     */
    public static final String STOCK_QUERY = "ae_stock_query";

    /** 当てる先の版。AE2 は 19.2 の系列、EMI は 1.1 の系列(内部の描画のクラスに当てるので、系列が変われば切る)。 */
    static final List<Requirement> AE2 = List.of(Requirement.of("ae2", "[19.2,19.3)"));
    static final List<Requirement> AE2_AND_EMI =
            List.of(Requirement.of("ae2", "[19.2,19.3)"), Requirement.of("emi", "[1.1,1.2)"));

    private EmiAeFeatures() {
    }

    public static List<Feature> all() {
        // ⚠ init はラムダの中で呼ぶ(メソッド参照 QuickCraft::init にしない)。メソッド参照は一覧を作る時点で
        // クラスを引くので、専用サーバでも画面のクラス(KeyMapping など)を読み込んでしまう
        return List.of(
                feature(STOCK, AE2_AND_EMI, true, bus -> { }),
                feature(CRAFT_CONFIRM, AE2_AND_EMI, true, bus -> { }),
                feature(QUICK_CRAFT, AE2_AND_EMI, true, bus -> gomacraft.utility.emiae.client.QuickCraft.init(bus)),
                // サーバと画面の両方: 通信の口(サーバは答え、画面は問う。どちらも optional)
                feature(STOCK_QUERY, AE2, false, bus -> gomacraft.utility.emiae.net.ServerStock.init(bus)));
    }

    /**
     * @param clientOnly 画面にだけ効くか(専用サーバでは切る)
     * @param init       有効なときに、Mod の組み立ての中で 1 回呼ぶもの
     */
    private static Feature feature(String id, List<Requirement> requirements, boolean clientOnly,
            Consumer<IEventBus> init) {
        return new Feature() {
            @Override
            public void init(FeatureContext context) {
                init.accept(context.modBus());
            }

            @Override
            public String id() {
                return id;
            }

            @Override
            public List<Requirement> requirements() {
                return requirements;
            }

            @Override
            public boolean clientOnly() {
                return clientOnly;
            }
        };
    }
}
