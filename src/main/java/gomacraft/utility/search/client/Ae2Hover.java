package gomacraft.utility.search.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import net.neoforged.fml.ModList;

/** AE2 の端末で、マウスの下の品から検索語を作る。 */
final class Ae2Hover {
    private Ae2Hover() {
    }

    /**
     * 端末の一覧・持ち物の枠の品を先に見て、無ければ EMI の一覧の品を見る。
     *
     * @param modId true なら {@code @<名前空間>}(AE2 の Mod の検索)、false なら今の言語の表示名
     * @return 検索語(マウスの下に品が無ければ null)
     */
    static String hoveredSearchText(MEStorageScreen<?> screen, boolean modId) {
        double[] mouse = Ae2SearchKey.mouse();
        var under = screen.getStackUnderMouse(mouse[0], mouse[1]);
        if (under != null && under.stack() != null) {
            AEKey key = under.stack().what();
            return modId ? "@" + key.getModId() : key.getDisplayName().getString();
        }
        if (ModList.get().isLoaded("emi")) {
            return EmiBridge.hoveredSearchText(modId);
        }
        return null;
    }
}
