package gomacraft.utility.emiae.client;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import gomacraft.utility.Features;
import gomacraft.utility.emiae.EmiAeFeatures;

/**
 * EMI の公開の差し込み口(EMI が注釈を探して呼ぶ)。
 *
 * <p>⚠ このクラスは EMI が在れば AE2 の有無に依らず読まれる。AE2 のクラスはここに書かず、機能が有効なときだけ
 * {@link CraftConfirmBounds} を呼ぶ(有効の判定に AE2 の在ることが入っている)。
 */
@EmiEntrypoint
public final class GomacraftEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // EMI の読み直しのたびに呼ばれる。止めている間に呼ばれても、動かし直したら効くように、止めているかは見ない
        // (中身は CraftConfirmBounds が毎回 guard で分ける)
        Features.registry().guardLoaded(EmiAeFeatures.CRAFT_CONFIRM, () -> CraftConfirmBounds.register(registry));
    }
}
