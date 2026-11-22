package gomacraft.utility.features.mekagun_server;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.Requirement;
import java.util.List;

/**
 * MekaWeapons の Meka-Gun を専用サーバで撃つと落ちる件の直し(goma-org/gomabase#2295)。
 *
 * <p>MekaWeapons 4.3 の {@code ItemMekaGun} は、サーバ側の処理({@code onUseTick}・{@code releaseUsing})から
 * クライアント専用の部品({@code BeamManager} と Mekanism の {@code SoundHandler})を呼ぶ。専用サーバには
 * クライアントのクラスが無いので、最初に撃った時点で {@code NoClassDefFoundError} になる(上流
 * omeranha/MekaWeapons#57。上流の 6871500 で直ったが、CurseForge の版はまだ出ていない)。
 *
 * <p>直し方: 専用サーバでだけ(mixin の {@code server} の一覧)、その 3 つの呼び出しを外す。どれもサーバでは
 * 意味の無い呼び出しである(ビームの表示はクライアントの描画、音は撃った本人のクライアントが自分の
 * {@code onUseTick} で鳴らす)。当たり判定と傷はサーバの処理のまま残る。
 *
 * <p>⚠ 版は 4.3 だけに縛る。上流が直した版が出て台帳の版が上がれば、この機能は {@code off(version …)} になって
 * 自分で止まる(直した版では当て先の呼び出しが無いので、当て続けると FAILED になる)。
 */
public final class MekaGunServerFix implements Feature {
    public static final String ID = "mekagun_server";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Requirement> requirements() {
        return List.of(Requirement.of("mekaweapons", "[4.3]"));
    }
}
