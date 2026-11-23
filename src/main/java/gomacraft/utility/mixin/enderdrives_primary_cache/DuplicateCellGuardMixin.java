package gomacraft.utility.mixin.enderdrives_primary_cache;

import com.sts15.enderdrives.inventory.DeduplicatedEnderCell;
import com.sts15.enderdrives.inventory.DuplicateCellGuard;
import gomacraft.utility.features.enderdrives_primary_cache.EnderDrivesPrimaryCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EnderDrives の {@code DuplicateCellGuard}: isPrimary の答えを同じ tick の中で使い回し、セルの付け外しで覚えた答えを捨てる
 * (理由は {@link EnderDrivesPrimaryCache})。
 */
@Mixin(DuplicateCellGuard.class)
public abstract class DuplicateCellGuardMixin {
    /** 覚えた答えがあれば、数え直さずに返す(元の処理の付け足し —— 重なりの印の付け直し —— も同じにする)。 */
    @Group(name = "gomacraft_utility$enderdrives_primary_cache$is_primary_head", min = 1)
    @Inject(method = "isPrimary", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gomacraftUtility$cachedPrimary(DeduplicatedEnderCell cell, CallbackInfoReturnable<Boolean> cir) {
        Boolean hit = EnderDrivesPrimaryCache.hit(cell, cell::enderdrives$setDuplicateOnGrid);
        if (hit != null) {
            cir.setReturnValue(hit);
        }
    }

    /**
     * 全部を数えた後の返り(メソッドの最後の返り)の答えだけを覚える。早い返りの道(ドライブがつながっていない・grid が
     * 無いなど)は、元の処理が重なりの印に触らないので覚えない(当たったときの印の付け直しが元と食い違わないように)。
     */
    @Group(name = "gomacraft_utility$enderdrives_primary_cache$is_primary_tail", min = 1)
    @Inject(method = "isPrimary", at = @At("TAIL"), require = 0)
    private static void gomacraftUtility$rememberPrimary(DeduplicatedEnderCell cell, CallbackInfoReturnable<Boolean> cir) {
        EnderDrivesPrimaryCache.remember(cell, cir.getReturnValueZ());
    }

    @Group(name = "gomacraft_utility$enderdrives_primary_cache$bind", min = 1)
    @Inject(method = "bind", at = @At("HEAD"), require = 0)
    private static void gomacraftUtility$onBind(CallbackInfo ci) {
        EnderDrivesPrimaryCache.invalidate();
    }

    @Group(name = "gomacraft_utility$enderdrives_primary_cache$unbind", min = 1)
    @Inject(method = "unbind", at = @At("HEAD"), require = 0)
    private static void gomacraftUtility$onUnbind(CallbackInfo ci) {
        EnderDrivesPrimaryCache.invalidate();
    }
}
