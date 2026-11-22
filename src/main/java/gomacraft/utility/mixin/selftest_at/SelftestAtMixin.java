package gomacraft.utility.mixin.selftest_at;

import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 起動の自己検査のための、わざと壊した mixin(メソッドは在るが、{@code @At} の当て先の呼び出しが無い)。
 * 当て先の {@link PlayerList} は、Mod の読み込みが済んだ後(サーバの組み立て)に初めて読まれるクラス。
 * 起動時の 1 行を出す前に当て先を読み込ませるので、最初の 1 行に FAILED が出ることも確かめる。
 */
@Mixin(PlayerList.class)
public abstract class SelftestAtMixin {
    @Group(name = "gomacraft_utility$selftest_at$tick", min = 1)
    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lgomacraft/utility/selftest/NoSuch;nothing()V"),
            require = 0)
    private void gomacraftUtility$selftestAt(CallbackInfo ci) {
    }
}
