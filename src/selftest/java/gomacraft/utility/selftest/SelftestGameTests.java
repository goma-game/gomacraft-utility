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
 * 起動の自己検査(配る jar には入らない)。わざと壊した機能 {@code selftest_broken} の mixin が当て損ねても、
 * サーバが起動してここまで来て、その機能だけが FAILED になっていることを確かめる。
 */
@GameTestHolder(GomacraftUtility.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SelftestGameTests {
    private SelftestGameTests() {
    }

    @GameTest(template = "empty")
    public static void brokenFeatureIsContained(GameTestHelper helper) {
        Optional<FeatureStatus> status = Features.registry().status("selftest_broken");
        helper.assertTrue(status.isPresent() && status.get().state() == FeatureState.FAILED,
                "selftest_broken は FAILED のはず: " + status);
        helper.succeed();
    }
}
