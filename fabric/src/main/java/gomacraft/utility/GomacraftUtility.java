package gomacraft.utility;

import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.mixinsupport.GomacraftMixinPlugin;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Mod の入口(両方の側)。レジストリ・ブロック・アイテムは足さない(いつでも外せる形を保つ)。通信を足すときは、
 * 送る前に相手がその channel を持つかを確かめる(この Mod の無い相手もいる)。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスの対。機能の初期化はここ、手元の設定と画面はクライアントの入口
 * ({@link GomacraftUtilityClient})。起動時の 1 行は、専用サーバではサーバの組み立ての始め、クライアントでは
 * ゲームが立ち上がったところで出す(どちらも全部の Mod の初期化の後。forge 版の FMLLoadCompleteEvent の代わり)。
 */
public final class GomacraftUtility implements ModInitializer {
    public static final String MOD_ID = "gomacraft_utility";

    @Override
    public void onInitialize() {
        FeatureRegistry registry = Features.registry();
        boolean dedicated = Features.dedicatedServer();
        registry.initFeatures(new FeatureContext(!dedicated, dedicated));
        if (dedicated) {
            ServerLifecycleEvents.SERVER_STARTING.register(server -> emitStatus(registry));
        }
    }

    /** 当て先のクラスを読み込ませて(遅れて読まれるクラスの当て損ねもここで出す)、機能ごとの状態を 1 行出す。 */
    static void emitStatus(FeatureRegistry registry) {
        GomacraftMixinPlugin.preloadTargets(registry, GomacraftUtility.class.getClassLoader());
        registry.emit();
    }
}
