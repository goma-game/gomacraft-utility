package gomacraft.utility.screenshot.net;

import gomacraft.utility.GomacraftUtility;
import gomacraft.utility.screenshot.ScreenshotRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * スクショの送り(クライアント → サーバの切れ端)と、その結果(サーバ → クライアント)。
 *
 * <p>⚠ 切れ端の大きさは読む側の codec が {@link ScreenshotRules#CHUNK_BYTES} で縛る(それより大きい切れ端は
 * 解く前に拒まれる)。
 */
public final class ScreenshotPayloads {
    private ScreenshotPayloads() {
    }

    /**
     * 1 個の切れ端。{@code upload} は送りの番号(送る側が決める)、{@code total} は 1 枚の全体の大きさ
     * (どの切れ端でも同じ値)、{@code index} は 0 から順の番号、{@code target} は行き先の種類
     * ({@link ScreenshotRules.Target} の番号。どの切れ端でも同じ値 —— チャンネルの ID は送らない)。
     */
    public record Chunk(int upload, int total, int index, int target, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GomacraftUtility.MOD_ID, "screenshot_chunk"));
        public static final StreamCodec<ByteBuf, Chunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Chunk::upload,
                ByteBufCodecs.VAR_INT, Chunk::total,
                ByteBufCodecs.VAR_INT, Chunk::index,
                ByteBufCodecs.VAR_INT, Chunk::target,
                ByteBufCodecs.byteArray(ScreenshotRules.CHUNK_BYTES), Chunk::data,
                Chunk::new);

        @Override
        public Type<Chunk> type() {
            return TYPE;
        }
    }

    /** 送りの結果({@link ScreenshotRules.Result} の番号)。 */
    public record Outcome(int upload, int result) implements CustomPacketPayload {
        public static final Type<Outcome> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GomacraftUtility.MOD_ID, "screenshot_result"));
        public static final StreamCodec<ByteBuf, Outcome> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Outcome::upload,
                ByteBufCodecs.VAR_INT, Outcome::result,
                Outcome::new);

        public static Outcome of(int upload, ScreenshotRules.Result result) {
            return new Outcome(upload, result.ordinal());
        }

        @Override
        public Type<Outcome> type() {
            return TYPE;
        }
    }
}
