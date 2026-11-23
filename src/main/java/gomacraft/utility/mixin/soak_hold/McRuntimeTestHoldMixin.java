package gomacraft.utility.mixin.soak_hold;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import gomacraft.utility.Features;
import gomacraft.utility.features.soak_hold.SoakHold;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * ワールドに入った後は、mc-runtime-test の毎 tick の処理を止める(理由は {@code SoakHold})。
 *
 * <p>⚠ 当て先は組み立ての依存に無い(mc-runtime-test は Maven に無い)。当たったかは、gomabase の箱が
 * {@link SoakHold#MARKER} の 1 行で見る(出なければ、箱の要約の「終わり方」が「決めた時間の前に終わった」になる)。
 */
@Mixin(targets = "me.earth.mc_runtime_test.McRuntimeTest", remap = false)
public abstract class McRuntimeTestHoldMixin {
    @Unique
    private static final Logger GOMACRAFT_UTILITY$LOGGER = LoggerFactory.getLogger("gomacraft_utility");

    @Unique
    private static boolean gomacraftUtility$holding;

    @Group(name = "gomacraft_utility$soak_hold$tick_hook", min = 1)
    @ModifyReturnValue(method = "tickHook()Z", at = @At("RETURN"), require = 0)
    private static boolean gomacraftUtility$holdTheWorld(boolean original) {
        if (!original) {
            return false;
        }
        return Features.guard(SoakHold.ID, () -> !gomacraftUtility$shouldHold(), true);
    }

    @Unique
    private static boolean gomacraftUtility$shouldHold() {
        if (gomacraftUtility$holding) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.player.tickCount < SoakHold.HOLD_AFTER_TICKS) {
            return false;
        }
        gomacraftUtility$holding = true;
        GOMACRAFT_UTILITY$LOGGER.info("{} (mc-runtime-test の終わらせを止めた)", SoakHold.MARKER);
        return true;
    }
}
