package gomacraft.utility.search.client;

import net.minecraft.client.gui.components.EditBox;
import net.neoforged.fml.ModList;

/** AE2 の端末の検索欄に検索語を入れる。 */
final class Ae2Search {
    private Ae2Search() {
    }

    /**
     * 検索欄が見えていれば入れる(AE2 の responder が検索を走らせる)。AE2 の設定で外の検索(EMI)を使っていて欄が
     * 隠れているときは、EMI の検索欄へ入れる(AE2 はそちらを読む)。
     *
     * @return 入れられたか(入れる先が無ければ false)
     */
    static boolean apply(EditBox field, String text) {
        if (field != null && field.isVisible()) {
            field.setValue(text);
            field.moveCursorToEnd(false);
            return true;
        }
        if (ModList.get().isLoaded("emi")) {
            EmiBridge.setSearchText(text);
            return true;
        }
        return false;
    }
}
