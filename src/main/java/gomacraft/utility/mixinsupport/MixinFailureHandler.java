package gomacraft.utility.mixinsupport;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureRegistry;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * この Mod の mixin の当て損ねを、その機能の FAILED に変え、起動は止めない(警告に落とす)。
 * ほかの Mod の mixin の扱いは変えない。
 */
public final class MixinFailureHandler implements IMixinErrorHandler {
    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return handle(mixin, th, action);
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return handle(mixin, th, action);
    }

    private static ErrorAction handle(IMixinInfo mixin, Throwable th, ErrorAction action) {
        String name = mixin == null ? null : mixin.getClassName();
        if (name == null || !name.startsWith(GomacraftMixinPlugin.MIXIN_PACKAGE)) {
            return action;
        }
        String feature = GomacraftMixinPlugin.featureOf(name);
        Features.registry().markFailed(feature == null ? name : feature, FeatureRegistry.describe(th));
        return ErrorAction.WARN;
    }
}
