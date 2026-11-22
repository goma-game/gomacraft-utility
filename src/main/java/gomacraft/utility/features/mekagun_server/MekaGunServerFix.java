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
 * <p>直し方: 専用サーバでだけ(mixin の {@code server} の一覧。{@link #serverOnly})、その 3 つの呼び出しを外す。
 * <ul>
 *   <li>{@code BeamManager.setBeam}・{@code removeBeam}: 4.3 は {@code !level.isClientSide()} の側(サーバ)でしか
 *       呼ばない。描画はクライアントの表を読むので、専用サーバの上で書いた表は、もともと誰の画面にも届いていない
 *       (専用サーバでは、直す前からビームは誰にも見えない —— 外して失うものは無い)。</li>
 *   <li>{@code SoundHandler.playSound}: 撃った本人のクライアントが、自分の側の {@code onUseTick} で鳴らす。</li>
 * </ul>
 * 当たり判定・傷・エネルギー・熱は、サーバの処理のまま残る(外すのは呼び出しの命令だけ)。
 *
 * <p>⚠ 版の幅は {@code [4.3,)}(fail-closed)。直っていない新しい版が出ても効き続け、上流が直して当て先の呼び出しが
 * 消えたら、当て損ねで FAILED になって赤く知らせる(黙って off にならない)。赤くなったら、この機能を外す。
 */
public final class MekaGunServerFix implements Feature {
    public static final String ID = "mekagun_server";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Requirement> requirements() {
        return List.of(Requirement.of("mekaweapons", "[4.3,)"));
    }

    @Override
    public boolean serverOnly() {
        return true;
    }
}
