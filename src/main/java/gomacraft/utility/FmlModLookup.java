package gomacraft.utility;

import gomacraft.utility.feature.ModLookup;
import java.util.Optional;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

/**
 * FML が見つけた Mod の一覧から版を引く。mixin の当否を決める時点(Mod の組み立てより前)でも使えるように、
 * ModList ではなく LoadingModList を読む。
 */
enum FmlModLookup implements ModLookup {
    INSTANCE;

    @Override
    public Optional<String> version(String modId) {
        for (ModInfo info : LoadingModList.get().getMods()) {
            if (info.getModId().equals(modId)) {
                return Optional.of(info.getVersion().toString());
            }
        }
        return Optional.empty();
    }
}
