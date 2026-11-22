package gomacraft.utility.mixin.selftest_broken;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 起動の自己検査のための、わざと壊した mixin(当て先のメソッドが無い)。機能 {@code selftest_broken} は
 * 自己検査のときだけ一覧に入るので、配った jar の中ではこの mixin は当たらない。
 *
 * <p>injector は全部 {@code require = 0} と {@code @Group(min = 1)} で書く(この Mod の約束 ——
 * {@code require} で落ちると Mixin は Error を投げ、起動ごと止まる。group の不足は警告に落とせる)。
 */
@Mixin(MinecraftServer.class)
public abstract class SelftestBrokenMixin {
    @Group(name = "gomacraft_utility$selftest_broken", min = 1)
    @Inject(method = "gomacraftUtilitySelftestNoSuchMethod", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$selftest(CallbackInfo ci) {
    }
}
