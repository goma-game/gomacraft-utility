package gomacraft.utility.selftest;

import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.FeatureState;
import gomacraft.utility.feature.FeatureStatus;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 起動の自己検査(配る jar には入らない)。わざと壊した機能(当て先のメソッドが無い・{@code @At} が合わない・
 * 当て先のクラスが無い)の mixin が当て損ねても、サーバが起動してここまで来て、それぞれの機能だけが
 * FAILED になっていることを確かめる。
 */
@GameTestHolder(GomacraftUtility.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SelftestGameTests {
    private SelftestGameTests() {
    }

    @GameTest(template = "empty")
    public static void brokenFeaturesAreContained(GameTestHelper helper) {
        for (String id : Features.SELFTEST_FEATURES) {
            Optional<FeatureStatus> status = Features.registry().status(id);
            helper.assertTrue(status.isPresent() && status.get().state() == FeatureState.FAILED,
                    id + " は FAILED のはず: " + status);
        }
        helper.succeed();
    }
}
