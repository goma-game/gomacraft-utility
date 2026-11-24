package gomacraft.utility.search.client;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import gomacraft.utility.mixin.explorerscompass_search.ExplorersCompassFilterAccessor;
import gomacraft.utility.mixin.explorerscompass_search.ExplorersCompassScreenAccessor;
import gomacraft.utility.search.CompassFilterSettings;
import gomacraft.utility.search.CompassQuery;
import gomacraft.utility.settings.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Explorer's Compass の検索の、印での絞り込み(決まりは {@link CompassFilters})。⚠ Nature's Compass のクラスを持たない。
 */
public final class ExplorersCompassFilters {
    private static final Set<CompassQuery.Kind> KINDS = EnumSet.of(CompassQuery.Kind.SOURCE,
            CompassQuery.Kind.GROUP, CompassQuery.Kind.DIMENSION);

    private ExplorersCompassFilters() {
    }

    /** Explorer's Compass。受け持ったら true。 */
    public static boolean apply(ExplorersCompassScreen screen) {
        if (!Settings.client().get(CompassFilterSettings.EXPLORERS)) {
            return false;
        }
        String raw = ((ExplorersCompassScreenAccessor) screen).gomacraftUtility$searchField().getValue();
        if (!CompassQuery.takesOver(raw, KINDS)) {
            return false;
        }
        List<CompassQuery.Term> terms = CompassQuery.parse(raw);
        ExplorersCompassFilterAccessor accessor = (ExplorersCompassFilterAccessor) screen;
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation key : accessor.gomacraftUtility$allowedStructureKeys()) {
            boolean hit = CompassQuery.matches(terms, kind -> switch (kind) {
                case NAME -> Arrays.asList(CompassEnglish.structure(StructureUtils.getPrettyStructureName(key), key));
                case SOURCE -> Arrays.asList(StructureUtils.getPrettyStructureSource(key), CompassFilters.namespace(key));
                case GROUP -> group(key);
                case DIMENSION -> CompassFilters.dimensions(ExplorersCompass.dimensionKeysForAllowedStructureKeys == null
                        ? List.of() : ExplorersCompass.dimensionKeysForAllowedStructureKeys.get(key));
                case TAG -> Arrays.asList();
            });
            if (hit) {
                out.add(key);
            }
        }
        accessor.gomacraftUtility$setStructureKeysMatchingSearch(out);
        accessor.gomacraftUtility$selectionList().refreshList(true);
        return true;
    }

    /** 構造物の群(Explorer's Compass の「グループ」)の名前(英語名を足したもの)と id。 */
    private static List<String> group(ResourceLocation key) {
        ResourceLocation type = ExplorersCompass.structureKeysToTypeKeys == null
                ? null : ExplorersCompass.structureKeysToTypeKeys.get(key);
        if (type == null) {
            return List.of();
        }
        return Arrays.asList(CompassEnglish.structure(StructureUtils.getPrettyStructureName(type), type), type.toString());
    }
}
