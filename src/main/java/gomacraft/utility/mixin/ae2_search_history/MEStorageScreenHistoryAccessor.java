package gomacraft.utility.mixin.ae2_search_history;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.widgets.AETextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AE2 の端末の検索欄(private)を読む。値を入れると、AE2 の responder が検索を走らせる。 */
@Mixin(MEStorageScreen.class)
public interface MEStorageScreenHistoryAccessor {
    @Accessor("searchField")
    AETextField gomacraftUtility$historySearchField();
}
