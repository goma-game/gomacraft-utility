package gomacraft.utility.mixin.selftest_missing_class;

import org.spongepowered.asm.mixin.Mixin;

/**
 * 起動の自己検査のための、わざと壊した mixin(当て先のクラスが無い)。Mixin はこの形を警告だけで
 * 飛ばすので、plugin がクラスの有無を確かめて、機能を FAILED にする。
 */
@Mixin(targets = "gomacraft.utility.selftest.NoSuchTarget")
public abstract class SelftestMissingClassMixin {
}
