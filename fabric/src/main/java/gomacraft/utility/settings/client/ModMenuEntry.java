package gomacraft.utility.settings.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu の一覧の「設定」から開く画面(fabric.mod.json の modmenu の入口)。Mod Menu が無ければ、この入口は
 * 呼ばれない(Mod Menu は組み立てにだけ使い、依存にはしない)。
 */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
