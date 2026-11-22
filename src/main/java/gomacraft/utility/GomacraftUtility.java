package gomacraft.utility;

import gomacraft.utility.feature.FeatureContext;
import gomacraft.utility.feature.FeatureRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

/**
 * Mod の入口。レジストリ・ブロック・アイテムは足さない(いつでも外せる形を保つ)。通信を足すときは、payload を
 * 全部 {@code optional()} にする(この Mod の無いクライアントも接続できるように)。
 */
@Mod(GomacraftUtility.MOD_ID)
public final class GomacraftUtility {
    public static final String MOD_ID = "gomacraft_utility";

    public GomacraftUtility(IEventBus modBus, Dist dist) {
        FeatureRegistry registry = Features.registry();
        registry.initFeatures(new FeatureContext(modBus, dist));
        // 全部の Mod の組み立てが済んだところで、機能ごとの状態を 1 行出す(gomabase の起動の検証が読む)
        modBus.addListener(FMLLoadCompleteEvent.class, event -> registry.emit());
    }
}
