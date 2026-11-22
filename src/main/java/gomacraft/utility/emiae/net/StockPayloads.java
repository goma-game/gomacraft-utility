package gomacraft.utility.emiae.net;

import appeng.api.stacks.AEKey;
import gomacraft.utility.GomacraftUtility;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 端末を閉じているときの在庫数の問い合わせ(画面 → サーバ)と答え(サーバ → 画面)。
 *
 * <p>⚠ 1 回に問える品は {@link #MAX_KEYS} まで(読む側の codec がそれを超えた分を拒む)。送るのは一覧に
 * 見えている品だけ。
 */
public final class StockPayloads {
    /** 1 回の問い合わせの品の上限(EMI の一覧の 1 頁は大きな画面でも数百)。 */
    public static final int MAX_KEYS = 256;

    private StockPayloads() {
    }

    /** 知らない種類の鍵(AE2 のアドオンの違い)は null で読まれるので、捨てる。 */
    private static final StreamCodec<RegistryFriendlyByteBuf, List<AEKey>> KEYS =
            AEKey.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_KEYS)).map(
                    list -> list.stream().filter(Objects::nonNull).toList(), list -> list);

    /** 画面 → サーバ: この品の在庫数とクラフト可を知りたい。 */
    public record Query(List<AEKey> keys) implements CustomPacketPayload {
        public static final Type<Query> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GomacraftUtility.MOD_ID, "stock_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> CODEC =
                KEYS.map(Query::new, Query::keys);

        @Override
        public Type<Query> type() {
            return TYPE;
        }
    }

    /** 1 品の答え。 */
    public record Entry(AEKey key, long stored, boolean craftable) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                AEKey.STREAM_CODEC, Entry::key,
                ByteBufCodecs.VAR_LONG, Entry::stored,
                ByteBufCodecs.BOOL, Entry::craftable,
                Entry::new);
    }

    /**
     * サーバ → 画面: 答え。{@code reachable} が偽なら、届くネットワークが無い(ワイヤレスの端末を持っていない・
     * 範囲の外・電力切れ)ので、画面は数を消す。
     */
    public record Reply(boolean reachable, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Reply> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GomacraftUtility.MOD_ID, "stock_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Reply::reachable,
                Entry.CODEC.apply(ByteBufCodecs.list(MAX_KEYS)), Reply::entries,
                Reply::new);

        public static Reply unreachable() {
            return new Reply(false, new ArrayList<>());
        }

        @Override
        public Type<Reply> type() {
            return TYPE;
        }
    }
}
