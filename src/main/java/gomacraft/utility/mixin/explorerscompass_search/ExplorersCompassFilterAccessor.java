package gomacraft.utility.mixin.explorerscompass_search;

import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.gui.StructureSearchList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Explorer's Compass の検索の結果を、印での絞り込み({@code CompassFilters})で入れ替える。 */
@Mixin(ExplorersCompassScreen.class)
public interface ExplorersCompassFilterAccessor {
    @Accessor("allowedStructureKeys")
    List<ResourceLocation> gomacraftUtility$allowedStructureKeys();

    @Accessor("structureKeysMatchingSearch")
    void gomacraftUtility$setStructureKeysMatchingSearch(List<ResourceLocation> keys);

    @Accessor("selectionList")
    StructureSearchList gomacraftUtility$selectionList();
}
