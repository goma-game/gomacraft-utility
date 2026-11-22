package gomacraft.utility.screenshot;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.screenshot.client.ShareClient;
import gomacraft.utility.screenshot.net.ScreenshotNet;
import gomacraft.utility.screenshot.server.ServerInbox;
import java.util.List;

/**
 * スクショの Discord への投稿(goma-org/gomabase#2295 の段 4)。
 *
 * <p>同意は 1 枚ごとのクリック: 撮ったときのチャット欄の行に「投稿」の文字を足し、押したときだけその 1 枚を
 * サーバへ送る。押さなければ何も送らない。サーバは受けた 1 枚を置き場へ書くだけで、Discord へ出すのは
 * サーバの外の Bot(この Mod は秘密も宛先も持たない)。
 */
public final class ScreenshotFeatures {
    /** クライアント: 撮ったときの行に「投稿」の文字を出し、押された 1 枚を送る。出すだけでは何も送らない。 */
    public static final String SHARE = "screenshot_share";
    /** サーバ: 送られた 1 枚を受けて置き場へ書く。⚠ 既定は切(受けるサーバだけが設定で入れる)。 */
    public static final String INBOX = "screenshot_inbox";

    private ScreenshotFeatures() {
    }

    public static List<Feature> all() {
        return List.of(new Share(), new Inbox());
    }

    private static final class Share implements Feature {
        @Override
        public String id() {
            return SHARE;
        }

        @Override
        public boolean clientOnly() {
            return true;
        }

        @Override
        public void init(FeatureContext context) {
            if (context.dist().isClient()) {
                ScreenshotNet.register(context.modBus(), id());
                ShareClient.init();
            }
        }
    }

    private static final class Inbox implements Feature {
        @Override
        public String id() {
            return INBOX;
        }

        @Override
        public boolean enabledByDefault() {
            return false;
        }

        /** 受け口はサーバの宣言(gomabase)が決める。手元の設定の画面には出さない。 */
        @Override
        public boolean shownInClientSettings() {
            return false;
        }

        @Override
        public void init(FeatureContext context) {
            // 口を開くのは専用サーバだけ(シングルの裏のサーバでは受けない —— 画面の側もボタンを出さない)
            if (context.dist().isDedicatedServer()) {
                ScreenshotNet.register(context.modBus(), id());
                ServerInbox.init();
            }
        }
    }
}
