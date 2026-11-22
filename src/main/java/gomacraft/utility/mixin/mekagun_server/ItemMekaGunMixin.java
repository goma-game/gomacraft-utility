package gomacraft.utility.mixin.mekagun_server;

import java.util.UUID;
import mekanism.common.registration.impl.SoundEventRegistryObject;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 専用サーバでだけ当てる({@code gomacraft_utility.mixins.json} の {@code server})。Meka-Gun のサーバ側の処理から、
 * クライアント専用の部品の呼び出しを外す(理由は {@code MekaGunServerFix})。
 *
 * <p>当て先は文字列で書く(MekaWeapons は組み立ての依存に入れない)。呼び出しの形は 4.3 の jar を
 * javap で読んで写した。
 */
@Mixin(targets = "meranha.mekaweapons.items.ItemMekaGun")
public abstract class ItemMekaGunMixin {
    /** ビームの表示の登録(クライアントの描画が読む表。サーバで書いても誰にも届かない)。 */
    @Group(name = "gomacraft_utility$mekagun_server$set_beam", min = 1)
    @Redirect(
            method = "onUseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeranha/mekaweapons/client/BeamManager;setBeam(Ljava/util/UUID;"
                            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FFFF)V"),
            require = 0)
    private void gomacraftUtility$skipSetBeam(UUID player, Vec3 from, Vec3 to, float r, float g, float b, float a) {
    }

    /** 撃った音(Mekanism のクライアントの音の部品。撃った本人のクライアントは自分の側で鳴らす)。 */
    @Group(name = "gomacraft_utility$mekagun_server$play_sound", min = 1)
    @Redirect(
            method = "onUseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/client/sound/SoundHandler;playSound("
                            + "Lmekanism/common/registration/impl/SoundEventRegistryObject;)V"),
            require = 0)
    private void gomacraftUtility$skipPlaySound(SoundEventRegistryObject<?> sound) {
    }

    /** ビームの表示の取り消し(上の登録の対)。 */
    @Group(name = "gomacraft_utility$mekagun_server$remove_beam", min = 1)
    @Redirect(
            method = "releaseUsing",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeranha/mekaweapons/client/BeamManager;removeBeam(Ljava/util/UUID;)V"),
            require = 0)
    private void gomacraftUtility$skipRemoveBeam(UUID player) {
    }
}
