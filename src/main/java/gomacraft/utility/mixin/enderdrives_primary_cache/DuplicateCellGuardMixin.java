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
        Boolean hit = EnderDrivesPrimaryCache.cached(cell);
        if (hit != null) {
            cell.enderdrives$setDuplicateOnGrid(!hit);
            cir.setReturnValue(hit);
        }
    }

    /** 数え直した答えを覚える。 */
    @Group(name = "gomacraft_utility$enderdrives_primary_cache$is_primary_return", min = 1)
    @Inject(method = "isPrimary", at = @At("RETURN"), require = 0)
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
