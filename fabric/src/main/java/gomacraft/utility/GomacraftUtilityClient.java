package gomacraft.utility;

import gomacraft.utility.settings.Settings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/**
 * Mod の入口(クライアントだけ)。Fabric Loader は main の入口({@link GomacraftUtility})を先に呼ぶので、機能が
 * {@code init} で手元の設定の項目を宣言した後に、ここでファイルを読む(forge 版の GomacraftUtility の後半の対)。
 */
public final class GomacraftUtilityClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Settings.client().load(FabricLoader.getInstance().getConfigDir(),
                LoggerFactory.getLogger(GomacraftUtility.MOD_ID)::warn);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> GomacraftUtility.emitStatus(Features.registry()));
    }
}
