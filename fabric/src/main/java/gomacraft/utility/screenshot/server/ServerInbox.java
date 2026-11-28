package gomacraft.utility.screenshot.server;

import gomacraft.utility.Features;
import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.screenshot.ScreenshotFeatures;
import gomacraft.utility.screenshot.ScreenshotRules;
import gomacraft.utility.screenshot.ScreenshotRules.Result;
import gomacraft.utility.screenshot.net.ScreenshotPayloads;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * サーバの側: 送られた切れ端を集め、揃った 1 枚を確かめて置き場へ書き、決まった 1 行をログに出す。
 *
 * <p>⚠ ログの行(`gomacraft_utility: screenshot queued`)には何の値も載せない。gomacraft-manager はこの行を見て
 * 置き場を取りに来るだけなので、ほかの行に同じ文字列が出ても害は無い(取りに行って空なだけ)。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスから写した。置き場・メタ・ログの 1 行・返事は forge 版と同じ(約束)。
 * 違うのは event と通信の口(Fabric API)だけ。
 */
public final class ServerInbox {
    /** manager が見る行(値は載せない)。⚠ 形は gomacraft-manager との約束。 */
    public static final String QUEUED_LINE = "gomacraft_utility: screenshot queued";

    private static final Logger LOGGER = LoggerFactory.getLogger(GomacraftUtility.MOD_ID);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final InboxState STATE = new InboxState();

    private ServerInbox() {
    }

    public static void init() {
        // 受け手を登録した channel だけが相手に名乗られる(クライアントの canSend がこれを見る)
        ServerPlayNetworking.registerGlobalReceiver(ScreenshotPayloads.Chunk.TYPE,
                (chunk, context) -> handle(chunk, context.player()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                Features.guard(ScreenshotFeatures.INBOX, () -> {
                    for (InboxState.Dropped d : STATE.prune(System.currentTimeMillis())) {
                        ServerPlayer player = server.getPlayerList().getPlayer(d.player());
                        if (player != null) {
                            reply(player, d.upload(), Result.BROKEN);
                        }
                    }
                });
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Features.guard(ScreenshotFeatures.INBOX,
                () -> STATE.forget(handler.getPlayer().getUUID())));
    }

    static Path spool() {
        return Spool.dir(FabricLoader.getInstance().getGameDir());
    }

    /** 切れ端を受ける(主スレッド —— Fabric API は受け手を主スレッドで呼ぶ)。 */
    static void handle(ScreenshotPayloads.Chunk chunk, ServerPlayer player) {
        FeatureRegistry registry = Features.registry();
        if (!registry.isOn(ScreenshotFeatures.INBOX)) {
            if (chunk.index() == 0 || STATE.abortUpload(player.getUUID(), chunk.upload())) {
                reply(player, chunk.upload(), Result.OFF);
            }
            return;
        }
        Features.guard(ScreenshotFeatures.INBOX, () -> accept(player, chunk));
    }

    private static void accept(ServerPlayer player, ScreenshotPayloads.Chunk chunk) {
        boolean spoolFull = false;
        if (chunk.index() == 0) {
            try {
                spoolFull = Spool.full(spool());
            } catch (Exception e) {
                LOGGER.warn("gomacraft_utility: スクショの置き場を読めない: {}", FeatureRegistry.describe(e));
                reply(player, chunk.upload(), Result.ERROR);
                return;
            }
        }
        InboxState.Step step = STATE.accept(player.getUUID(), chunk.upload(), chunk.total(), chunk.index(),
                chunk.target(), chunk.data(), System.currentTimeMillis(), spoolFull);
        if (step.result() != null) {
            reply(player, chunk.upload(), step.result());
        }
        if (step.done() != null) {
            store(player, chunk.upload(), step.done(), step.target());
        }
    }

    /** 揃った 1 枚を、主スレッドの外で確かめて書く。結果は主スレッドへ戻して返す。 */
    private static void store(ServerPlayer player, int upload, byte[] image, ScreenshotRules.Target target) {
        MinecraftServer server = player.level().getServer();
        // 名前と誰かは接続から決める(payload の中の値は使わない)
        String name = player.getGameProfile().name();
        UUID id = player.getUUID();
        Util.ioPool().execute(() -> {
            Result result;
            Optional<Result> bad = ScreenshotRules.check(image);
            if (bad.isPresent()) {
                result = bad.get();
            } else {
                try {
                    ScreenshotRules.Format format = ScreenshotRules.sniff(image).orElseThrow();
                    Spool.write(spool(), image, format, target, name, id, Instant.now(), RANDOM.nextInt());
                    result = Result.ACCEPTED;
                } catch (Exception e) {
                    LOGGER.warn("gomacraft_utility: スクショを書けない: {}", FeatureRegistry.describe(e));
                    result = Result.ERROR;
                }
            }
            Result outcome = result;
            server.execute(() -> {
                STATE.written();
                if (outcome == Result.ACCEPTED) {
                    LOGGER.info(QUEUED_LINE);
                }
                ServerPlayer still = server.getPlayerList().getPlayer(id);
                if (still != null) {
                    reply(still, upload, outcome);
                }
            });
        });
    }

    private static void reply(ServerPlayer player, int upload, Result result) {
        if (ServerPlayNetworking.canSend(player, ScreenshotPayloads.Outcome.TYPE)) {
            ServerPlayNetworking.send(player, ScreenshotPayloads.Outcome.of(upload, result));
        }
    }
}
