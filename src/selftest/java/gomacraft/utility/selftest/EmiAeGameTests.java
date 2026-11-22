package gomacraft.utility.selftest;

import appeng.api.stacks.AEItemKey;
import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.emiae.EmiAeFeatures;
import gomacraft.utility.emiae.net.ServerStock;
import gomacraft.utility.emiae.net.StockPayloads;
import gomacraft.utility.feature.FeatureState;
import gomacraft.utility.feature.FeatureStatus;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 段 2(EMI と AE2)の自己検査。自己検査のサーバは AE2 と EMI を積んで起動する(build.gradle の localRuntime)。
 *
 * <ul>
 *   <li>画面だけの機能は、専用サーバでは {@code off(client only)}(サーバで効いて見えない)。</li>
 *   <li>在庫数の問い合わせの口はサーバで {@code on} で、ワイヤレスの端末を持たない人には「届かない」と答える
 *       (範囲・端末の無い人に在庫を見せない)。</li>
 * </ul>
 */
@GameTestHolder(GomacraftUtility.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmiAeGameTests {
    private EmiAeGameTests() {
    }

    @GameTest(template = "empty")
    public static void screenFeaturesAreOffOnTheServer(GameTestHelper helper) {
        for (String id : List.of(EmiAeFeatures.STOCK, EmiAeFeatures.CRAFT_CONFIRM, EmiAeFeatures.QUICK_CRAFT,
                EmiAeFeatures.GRID_CRAFT)) {
            Optional<FeatureStatus> status = Features.registry().status(id);
            helper.assertTrue(status.isPresent() && status.get().state() == FeatureState.OFF
                    && "client only".equals(status.get().reason()), id + " は off(client only) のはず: " + status);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stockQueryAnswersUnreachableWithoutATerminal(GameTestHelper helper) {
        Optional<FeatureStatus> status = Features.registry().status(EmiAeFeatures.STOCK_QUERY);
        helper.assertTrue(status.isPresent() && status.get().state() == FeatureState.ON,
                "ae_stock_query は on のはず: " + status);
        // Curios の枠を読む道(CuriosStacks)も、ここで 1 回通す(ae2wtlib の端末は Curios の枠に着けられる)
        helper.assertTrue(ModList.get().isLoaded("curios"), "自己検査に Curios が積まれていない(Curios の道を通らない)");
        // 仮の人は世界に入れない(入れると Curios が仮の接続へ同期を送ろうとして落ちる)
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        StockPayloads.Reply reply = ServerStock.answer(player, List.of(AEItemKey.of(Items.DIAMOND)));
        helper.assertFalse(reply.reachable(), "端末を持たない人に届くと答えた: " + reply);
        helper.assertTrue(reply.entries().isEmpty(), "届かないのに数を答えた: " + reply);
        helper.succeed();
    }
}
