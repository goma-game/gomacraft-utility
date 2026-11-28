package gomacraft.utility.chatimage;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureContext;
import java.util.List;

/**
 * チャットの Discord の画像をゲームの中で見る機能と、チャットの URL をリンクにする機能(goma-game/gomacraft-utility#47)。
 *
 * <p>どちらもクライアントの画面だけで効く。中身は {@code chatimage/client/} に置く(この定義はサーバでも読まれる)。
 */
public final class ChatImageFeatures {
    /** チャットの Discord の画像のリンクに、マウスを乗せると小さく出し、押すとゲームの中の画面で出す。 */
    public static final String VIEWER = "chat_image_viewer";
    /** 受け取ったチャットの行の http / https の URL を、押せるリンクにする(表示だけ。署名した中身は変えない)。 */
    public static final String LINK = "chat_link";

    private ChatImageFeatures() {
    }

    public static List<Feature> all() {
        return List.of(new Viewer(), new Link());
    }

    private static final class Viewer implements Feature {
        @Override
        public String id() {
            return VIEWER;
        }

        @Override
        public boolean clientOnly() {
            return true;
        }

        @Override
        public void init(FeatureContext context) {
            if (context.dist().isClient()) {
                gomacraft.utility.chatimage.client.ChatImageClient.init();
            }
        }
    }

    private static final class Link implements Feature {
        @Override
        public String id() {
            return LINK;
        }

        @Override
        public boolean clientOnly() {
            return true;
        }

        @Override
        public void init(FeatureContext context) {
            if (context.dist().isClient()) {
                gomacraft.utility.chatimage.client.ChatLinkClient.init();
            }
        }
    }
}
