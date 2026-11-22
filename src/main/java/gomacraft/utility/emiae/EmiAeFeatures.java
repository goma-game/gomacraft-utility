package gomacraft.utility.emiae;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.Requirement;
import java.util.List;

/**
 * 段 2(EMI と AE2)の機能(goma-org/gomabase#2295)。
 *
 * <p>⚠ どれも、AE2 のネットワークの中身を EMI の「手持ち」({@code EmiPlayerInventory})へ流さない。流すと EMI が
 * ネットワークの中身の変わるたびに「作れるレシピ」を作り直し、端末が重くなる(EMI Link を外した理由 ——
 * goma-org/gomabase#2286)。在庫の数は、この Mod が持つ索引から描画のときに読むだけにする。
 */
public final class EmiAeFeatures {
    /** EMI の一覧に、AE のネットワークの在庫数と「クラフト可」の印を出す(端末を開いている間)。 */
    public static final String STOCK = "emi_ae_stock";
    /** AE2 のクラフト計画の画面(CraftConfirmScreen)でも EMI の一覧を出す。 */
    public static final String CRAFT_CONFIRM = "emi_ae_craft_confirm";

    /** 当てる先の版。AE2 は 19.2 の系列、EMI は 1.1 の系列(内部の描画のクラスに当てるので、系列が変われば切る)。 */
    static final List<Requirement> AE2_AND_EMI =
            List.of(Requirement.of("ae2", "[19.2,19.3)"), Requirement.of("emi", "[1.1,1.2)"));

    private EmiAeFeatures() {
    }

    public static List<Feature> all() {
        return List.of(clientFeature(STOCK), clientFeature(CRAFT_CONFIRM));
    }

    private static Feature clientFeature(String id) {
        return new Feature() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<Requirement> requirements() {
                return AE2_AND_EMI;
            }

            @Override
            public boolean clientOnly() {
                return true;
            }
        };
    }
}
