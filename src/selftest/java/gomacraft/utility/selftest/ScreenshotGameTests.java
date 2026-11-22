package gomacraft.utility.selftest;

import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.FeatureState;
import gomacraft.utility.feature.FeatureStatus;
import gomacraft.utility.screenshot.ScreenshotFeatures;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * スクショの投稿(段 4)の起動の自己検査。⚠ サーバの受け口は既定で切(設定で入れたサーバだけが受ける —— 同意を
 * 既定で切に保つ約束)。専用サーバでクライアントの機能を初期化しても壊れない(画面の部品を読みに行かない)。
 */
@GameTestHolder(GomacraftUtility.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScreenshotGameTests {
    private ScreenshotGameTests() {
    }

    @GameTest(template = "empty")
    public static void inboxIsOffByDefaultAndShareDoesNotBreakTheServer(GameTestHelper helper) {
        Optional<FeatureStatus> inbox = Features.registry().status(ScreenshotFeatures.INBOX);
        helper.assertTrue(inbox.isPresent() && inbox.get().state() == FeatureState.OFF,
                "screenshot_inbox は既定で切のはず: " + inbox);
        Optional<FeatureStatus> share = Features.registry().status(ScreenshotFeatures.SHARE);
        // 画面だけの機能なので専用サーバでは off(client only)。⚠ FAILED でないこと(サーバで画面の部品を読みに行かない)
        helper.assertTrue(share.isPresent() && share.get().state() == FeatureState.OFF,
                "screenshot_share は専用サーバでは off(client only)のはず: " + share);
        helper.succeed();
    }
}
