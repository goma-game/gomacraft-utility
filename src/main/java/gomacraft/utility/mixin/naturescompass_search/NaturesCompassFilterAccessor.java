package gomacraft.utility.mixin.naturescompass_search;

import com.chaosthedude.naturescompass.gui.BiomeSearchList;
import com.chaosthedude.naturescompass.gui.NaturesCompassScreen;
import java.util.List;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Nature's Compass の検索の結果を、印での絞り込み({@code CompassFilters})で入れ替える。 */
@Mixin(NaturesCompassScreen.class)
public interface NaturesCompassFilterAccessor {
    @Accessor("allowedBiomes")
    List<Biome> gomacraftUtility$allowedBiomes();

    @Accessor("biomesMatchingSearch")
    void gomacraftUtility$setBiomesMatchingSearch(List<Biome> biomes);

    @Accessor("selectionList")
    BiomeSearchList gomacraftUtility$selectionList();
}
