package gomacraft.utility.mixin.ae2_search_sync_toggle;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.widgets.AETextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AE2 の端末の検索欄(private)を読む(ダブルクリックが欄の上かを見る)。 */
@Mixin(MEStorageScreen.class)
public interface MEStorageScreenSyncAccessor {
    @Accessor("searchField")
    AETextField gomacraftUtility$syncSearchField();
}
