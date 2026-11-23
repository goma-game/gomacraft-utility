package gomacraft.utility.mixin.enderdrives_primary_cache;

import appeng.blockentity.storage.DriveBlockEntity;
import gomacraft.utility.features.enderdrives_primary_cache.EnderDrivesPrimaryCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 のドライブの中身(セルの出し入れ)と、つながり・電力が変わった: EnderDrives の代表の答えを捨てる
 * (代表の選び方は、ドライブがつながっているか・どのスロットにどのセルが在るかで決まる —— 理由は
 * {@link EnderDrivesPrimaryCache})。
 */
@Mixin(DriveBlockEntity.class)
public abstract class DriveBlockEntityMixin {
    @Group(name = "gomacraft_utility$enderdrives_primary_cache$drive_inventory", min = 1)
    @Inject(method = "onChangeInventory", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$onChangeInventory(CallbackInfo ci) {
        EnderDrivesPrimaryCache.invalidate();
    }

    @Group(name = "gomacraft_utility$enderdrives_primary_cache$drive_state", min = 1)
    @Inject(method = "onMainNodeStateChanged", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$onStateChanged(CallbackInfo ci) {
        EnderDrivesPrimaryCache.invalidate();
    }
}
