package gomacraft.utility.emiae.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import gomacraft.utility.Features;
import gomacraft.utility.emiae.EmiAeFeatures;

/**
 * AE2 のクラフト計画の画面でも EMI の一覧を出す。
 *
 * <p>EMI は、枠(slot)の無い画面には一覧を出さない({@code EmiScreenBase.of})。クラフト計画の画面は枠を
 * 持たないので、EMI の公開の口({@code addGenericScreenBoundsProvider})で画面の範囲を教える。mixin は使わない。
 * 派生の画面(アドオンの端末)も拾うよう、型ではなく instanceof で見る。
 */
final class CraftConfirmBounds {
    private CraftConfirmBounds() {
    }

    static void register(EmiRegistry registry) {
        registry.addGenericScreenBoundsProvider(screen -> {
            if (!(screen instanceof CraftConfirmScreen confirm)) {
                return null;
            }
            return Features.guard(EmiAeFeatures.CRAFT_CONFIRM,
                    () -> new Bounds(confirm.getGuiLeft(), confirm.getGuiTop(), confirm.getXSize(), confirm.getYSize()),
                    null);
        });
    }
}
