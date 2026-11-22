package gomacraft.utility.settings.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Mod の一覧の「設定」の入口を登録する(画面の側だけで呼ぶ。このクラスは画面のクラスを引く)。 */
public final class SettingsScreens {
    private SettingsScreens() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, parent) -> new SettingsScreen(parent));
    }
}
