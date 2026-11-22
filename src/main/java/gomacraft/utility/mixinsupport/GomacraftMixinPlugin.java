package gomacraft.utility.mixinsupport;

import gomacraft.utility.Features;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * mixin の当否を、機能の状態で決める。mixin は {@code gomacraft.utility.mixin.<機能の名前>.*} に置く。
 * 有効でない機能の mixin は当てない。当て損ねは {@link MixinFailureHandler} が、その機能の FAILED に変える。
 */
public final class GomacraftMixinPlugin implements IMixinConfigPlugin {
    public static final String MIXIN_PACKAGE = "gomacraft.utility.mixin.";

    @Override
    public void onLoad(String mixinPackage) {
        Mixins.registerErrorHandlerClass(MixinFailureHandler.class.getName());
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String feature = featureOf(mixinClassName);
        // 包みの形に合わない mixin は、どの機能のものか分からないので当てない
        return feature != null && Features.registry().isOn(feature);
    }

    /** mixin のクラスの名前から機能の名前を引く(包みの形に合わなければ null)。 */
    public static String featureOf(String mixinClassName) {
        if (mixinClassName == null || !mixinClassName.startsWith(MIXIN_PACKAGE)) {
            return null;
        }
        String rest = mixinClassName.substring(MIXIN_PACKAGE.length());
        int dot = rest.indexOf('.');
        return dot <= 0 ? null : rest.substring(0, dot);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
