package gomacraft.utility.mixin.ae2_tooltip_search_cache;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AE2 の端末の品の一覧(Repo)を読む(ツールチップの文を先に作り置くため)。 */
@Mixin(MEStorageScreen.class)
public interface MEStorageScreenRepoAccessor {
    @Accessor("repo")
    Repo gomacraftUtility$tooltipRepo();
}
