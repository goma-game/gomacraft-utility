package gomacraft.utility.mixinsupport;

import gomacraft.utility.Features;
import gomacraft.utility.feature.FeatureRegistry;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

/**
 * mixin の当否を、機能の状態で決める。mixin は {@code gomacraft.utility.mixin.<機能の名前>.*} に置く。
 *
 * <ul>
 *   <li>有効でない機能の mixin は当てない。</li>
 *   <li>当て先のクラスが無ければ、その機能を FAILED にして当てない(Mixin 自身は警告だけで飛ばすので、
 *       放っておくと機能が {@code on} のまま黙って効かない)。</li>
 *   <li>当て損ね(メソッド・{@code @At} が合わない)は {@link MixinFailureHandler} が FAILED に変える。</li>
 *   <li>当てた先のクラスを覚えておき、起動時の 1 行の前に {@link #preloadTargets} で読み込ませる
 *       (遅れて読まれるクラスの当て損ねも、最初の 1 行に出すため)。</li>
 * </ul>
 */
public final class GomacraftMixinPlugin implements IMixinConfigPlugin {
    public static final String MIXIN_PACKAGE = "gomacraft.utility.mixin.";

    /** 機能の名前 → 当てる先のクラス(当てると決めたものだけ)。 */
    private static final Map<String, Set<String>> TARGETS = new LinkedHashMap<>();

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
        if (feature == null) {
            return false;
        }
        FeatureRegistry registry = Features.registry();
        if (!registry.isOn(feature)) {
            return false;
        }
        if (ClassInfo.forName(targetClassName) == null) {
            registry.markFailed(feature, "target class missing: " + targetClassName);
            return false;
        }
        synchronized (TARGETS) {
            TARGETS.computeIfAbsent(feature, f -> new LinkedHashSet<>()).add(targetClassName);
        }
        return true;
    }

    /**
     * 有効な機能の当て先のクラスを読み込ませる(初期化はしない)。読み込みで mixin が当たり、当て損ねは
     * {@link MixinFailureHandler} が FAILED に変える。読み込み自体の失敗も、その機能の FAILED にする。
     */
    public static void preloadTargets(FeatureRegistry registry, ClassLoader loader) {
        Map<String, Set<String>> targets;
        synchronized (TARGETS) {
            targets = new LinkedHashMap<>(TARGETS);
        }
        for (Map.Entry<String, Set<String>> entry : targets.entrySet()) {
            for (String target : entry.getValue()) {
                if (!registry.isOn(entry.getKey())) {
                    break;
                }
                try {
                    Class.forName(target, false, loader);
                } catch (Throwable t) {
                    registry.markFailed(entry.getKey(), "preload " + target + ": " + FeatureRegistry.describe(t));
                }
            }
        }
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
