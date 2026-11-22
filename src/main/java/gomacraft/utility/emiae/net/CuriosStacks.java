package gomacraft.utility.emiae.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Curios の枠に着けたワイヤレスの端末も拾う(ae2wtlib は端末を Curios の枠に着けられる)。
 *
 * <p>⚠ Curios が入っているときだけ呼ぶ(このクラスは Curios のクラスを引く)。
 */
final class CuriosStacks {
    private CuriosStacks() {
    }

    static List<ItemStack> of(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var equipped = handler.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                stacks.add(equipped.getStackInSlot(i));
            }
        });
        return stacks;
    }
}
