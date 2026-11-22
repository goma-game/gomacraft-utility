package gomacraft.utility.mixin.selftest_broken;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 起動の自己検査のための、わざと壊した mixin(当て先のメソッドが無い)。機能 {@code selftest_broken} は
 * 自己検査のときだけ一覧に入るので、配った jar の中ではこの mixin は当たらない。
 */
@Mixin(MinecraftServer.class)
public abstract class SelftestBrokenMixin {
    @Inject(method = "gomacraftUtilitySelftestNoSuchMethod", at = @At("HEAD"))
    private void gomacraftUtility$selftest(CallbackInfo ci) {
    }
}
