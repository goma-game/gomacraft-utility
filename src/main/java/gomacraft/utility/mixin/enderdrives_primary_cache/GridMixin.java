package gomacraft.utility.mixin.enderdrives_primary_cache;

import appeng.me.Grid;
import gomacraft.utility.features.enderdrives_primary_cache.EnderDrivesPrimaryCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 の grid のノードが増えた・減った(ドライブの出入り・grid の組み直しも、この 2 つを通る): EnderDrives の代表の
 * 答えを捨てる(理由は {@link EnderDrivesPrimaryCache})。
 */
@Mixin(Grid.class)
public abstract class GridMixin {
    @Group(name = "gomacraft_utility$enderdrives_primary_cache$grid_add", min = 1)
    @Inject(method = "add", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$onAdd(CallbackInfo ci) {
        EnderDrivesPrimaryCache.invalidate();
    }

    @Group(name = "gomacraft_utility$enderdrives_primary_cache$grid_remove", min = 1)
    @Inject(method = "remove", at = @At("HEAD"), require = 0)
    private void gomacraftUtility$onRemove(CallbackInfo ci) {
        EnderDrivesPrimaryCache.invalidate();
    }
}
