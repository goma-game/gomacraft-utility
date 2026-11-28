package gomacraft.utility;

import gomacraft.utility.feature.ModLookup;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * Fabric Loader が見つけた Mod の一覧から版を引く(forge 版の FmlModLookup の対)。mixin の当否を決める時点でも
 * 引ける(Loader は mixin より先に Mod を全部見つけている)。
 */
public enum FabricModLookup implements ModLookup {
    INSTANCE;

    @Override
    public Optional<String> version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public boolean matches(String version, String range) {
        try {
            return VersionPredicate.parse(range).test(Version.parse(version));
        } catch (VersionParsingException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
