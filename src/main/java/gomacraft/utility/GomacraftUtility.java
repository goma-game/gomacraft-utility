package gomacraft.utility;

import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.mixinsupport.GomacraftMixinPlugin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

/**
 * Mod の入口。レジストリ・ブロック・アイテムは足さない(いつでも外せる形を保つ)。通信を足すときは、payload を
 * 全部 {@code optional()} にし、送る前に相手がその channel を持つかを確かめる(この Mod の無い相手もいる)。
 */
@Mod(GomacraftUtility.MOD_ID)
public final class GomacraftUtility {
    public static final String MOD_ID = "gomacraft_utility";

    public GomacraftUtility(IEventBus modBus, Dist dist) {
        FeatureRegistry registry = Features.registry();
        registry.initFeatures(new FeatureContext(modBus, dist));
        // 全部の Mod の組み立てが済んだところで、当て先のクラスを読み込ませて(遅れて読まれるクラスの当て損ねも
        // ここで出す)、機能ごとの状態を 1 行出す(gomabase の起動の検証が読む)
        modBus.addListener(FMLLoadCompleteEvent.class, event -> {
            GomacraftMixinPlugin.preloadTargets(registry, GomacraftUtility.class.getClassLoader());
            registry.emit();
        });
    }
}
