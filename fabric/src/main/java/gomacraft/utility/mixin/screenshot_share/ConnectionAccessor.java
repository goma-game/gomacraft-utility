package gomacraft.utility.mixin.screenshot_share;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 送りの列の空きを見るための口(fabric 版だけ)。26.2 の {@link Connection} は channel を外へ出さない
 * (1.21.1 の {@code channel()} が無い)ので、field を読む。
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    Channel gomacraftUtility$channel();
}
