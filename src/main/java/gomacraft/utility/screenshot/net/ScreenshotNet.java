package gomacraft.utility.screenshot.net;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.screenshot.ScreenshotFeatures;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** スクショの通信の口。クライアントは送るために、専用サーバは受けるために、同じ形で登録する。 */
public final class ScreenshotNet {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private ScreenshotNet() {
    }

    /**
     * 1 回だけ登録する(2 つの機能のどちらから呼ばれても重ならない)。
     *
     * @param feature 登録を頼んだ機能(登録が例外を投げたら、この機能だけを FAILED にする)
     */
    public static void register(IEventBus modBus, String feature) {
        if (REGISTERED.compareAndSet(false, true)) {
            modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
                // ⚠ mod bus の listener の例外は Mod の読み込みごと止める。機能の FAILED に落とす
                try {
                    onRegister(event);
                } catch (Throwable t) {
                    Features.registry().markFailed(feature, FeatureRegistry.describe(t));
                }
            });
        }
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        // optional: この Mod の無い相手とも接続できる(送る側は、送る前に相手の口を確かめる)
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ScreenshotPayloads.Chunk.TYPE, ScreenshotPayloads.Chunk.CODEC,
                gomacraft.utility.screenshot.server.ServerInbox::handle);
        registrar.playToClient(ScreenshotPayloads.Outcome.TYPE, ScreenshotPayloads.Outcome.CODEC,
                (outcome, context) -> Features.guard(ScreenshotFeatures.SHARE,
                        () -> gomacraft.utility.screenshot.client.ShareClient.onOutcome(outcome)));
    }
}
