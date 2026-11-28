package gomacraft.utility.screenshot.net;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * スクショの通信の口。クライアントは送るために、専用サーバは受けるために、同じ形で payload の型を登録する。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスの対。Fabric では型の登録と受け手の登録が別で、相手に名乗る channel は
 * 受け手を登録したものだけ(受け手はクライアントが {@code ShareClient}、専用サーバが {@code ServerInbox} で登録する)。
 * だから送る前の {@code canSend} は、forge 版の {@code hasChannel} と同じ意味になる(相手が受け口を開いているか)。
 * Fabric の custom payload は、もともと optional(この Mod の無い相手とも接続できる)。
 */
public final class ScreenshotNet {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private ScreenshotNet() {
    }

    /**
     * 1 回だけ登録する(2 つの機能のどちらから呼ばれても重ならない)。
     *
     * @param feature 登録を頼んだ機能(登録が例外を投げたら、この機能だけを FAILED にする)
     */
    public static void registerTypes(String feature) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            PayloadTypeRegistry.serverboundPlay().register(ScreenshotPayloads.Chunk.TYPE, ScreenshotPayloads.Chunk.CODEC);
            PayloadTypeRegistry.clientboundPlay().register(ScreenshotPayloads.Outcome.TYPE,
                    ScreenshotPayloads.Outcome.CODEC);
        } catch (Throwable t) {
            Features.registry().markFailed(feature, FeatureRegistry.describe(t));
        }
    }
}
