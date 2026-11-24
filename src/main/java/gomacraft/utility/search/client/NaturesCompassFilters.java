package gomacraft.utility.search.client;

import com.chaosthedude.naturescompass.NaturesCompass;
import com.chaosthedude.naturescompass.gui.NaturesCompassScreen;
import com.chaosthedude.naturescompass.util.BiomeUtils;
import gomacraft.utility.mixin.naturescompass_search.NaturesCompassFilterAccessor;
import gomacraft.utility.mixin.naturescompass_search.NaturesCompassScreenAccessor;
import gomacraft.utility.search.CompassFilterSettings;
import gomacraft.utility.search.CompassQuery;
import gomacraft.utility.settings.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Nature's Compass の検索の、印での絞り込み(決まりは {@link CompassFilters})。⚠ Explorer's Compass のクラスを持たない
 * (片方のコンパスだけの組でも読めるように、コンパスごとに分ける)。
 */
public final class NaturesCompassFilters {
    private static final Set<CompassQuery.Kind> KINDS = EnumSet.of(CompassQuery.Kind.SOURCE,
            CompassQuery.Kind.TAG, CompassQuery.Kind.DIMENSION);

    private NaturesCompassFilters() {
    }

    /** Nature's Compass。受け持ったら true。 */
    public static boolean apply(NaturesCompassScreen screen) {
        if (!Settings.client().get(CompassFilterSettings.NATURES)) {
            return false;
        }
        String raw = ((NaturesCompassScreenAccessor) screen).gomacraftUtility$searchField().getValue();
        if (!CompassQuery.takesOver(raw, KINDS)) {
            return false;
        }
        List<CompassQuery.Term> terms = CompassQuery.parse(raw);
        NaturesCompassFilterAccessor accessor = (NaturesCompassFilterAccessor) screen;
        Level level = screen.level;
        List<Biome> out = new ArrayList<>();
        for (Biome biome : accessor.gomacraftUtility$allowedBiomes()) {
            ResourceLocation key = BiomeUtils.getKeyForBiome(level, biome).orElse(null);
            boolean hit = CompassQuery.matches(terms, kind -> switch (kind) {
                case NAME -> Arrays.asList(CompassEnglish.biome(BiomeUtils.getBiomeNameForDisplay(level, biome), key));
                case SOURCE -> Arrays.asList(BiomeUtils.getBiomeSource(level, biome), CompassFilters.namespace(key));
                case TAG -> Arrays.asList(BiomeUtils.getBiomeTags(level, biome));
                case DIMENSION -> CompassFilters.dimensions(key == null || NaturesCompass.dimensionKeysForAllowedBiomeKeys == null
                        ? List.of() : NaturesCompass.dimensionKeysForAllowedBiomeKeys.get(key));
                case GROUP -> List.of();
            });
            if (hit) {
                out.add(biome);
            }
        }
        accessor.gomacraftUtility$setBiomesMatchingSearch(out);
        accessor.gomacraftUtility$selectionList().refreshList(true);
        return true;
    }
}
