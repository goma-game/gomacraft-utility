package gomacraft.utility.features.soak_hold;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.Requirement;
import java.util.List;

/**
 * 長時間の確かめの開発用の保持(goma-org/gomabase#2295)。⚠ 配る形では何もしない。
 *
 * <p>gomabase のクライアントの起動の箱は、mc-runtime-test(ワールドを作って入り、100 tick で終わらせる Mod)で
 * ワールドに入る。長時間の確かめ({@code client-boot.sh soak})では、ワールドに入ったまま何時間も置きたいが、
 * mc-runtime-test には留める設定が無い。そこで、ワールドに入った後は mc-runtime-test の毎 tick の処理
 * ({@code McRuntimeTest.tickHook} が真のときだけ走る)を止め、終わらせないようにする。
 *
 * <p>⚠ 一覧に入るのは、起動の引数に {@code -Dgomacraft_utility.soak=hold} があるときだけ
 * ({@link gomacraft.utility.Features#SOAK_PROPERTY})。配った jar を普通に起動すると一覧に無く、mixin も当たらない
 * (起動の自己検査の、わざと壊した機能と同じ考え方)。mc-runtime-test が無ければ、要るものが無いので off。
 */
public final class SoakHold implements Feature {
    public static final String ID = "soak_hold";
    /** 保持を始めた 1 行。gomabase の箱(local/client-boot/boot.py の SOAK_MARKER)がこの文字列を待つ。 */
    public static final String MARKER = "gomacraft_utility: soak: holding the world";
    /**
     * 保持を始める tick。⚠ mc-runtime-test はプレイヤーの tick が 100 に届くと終わらせるので、それより前にする。
     * 作成画面を閉じる処理(同じ tick の処理の中)が済むまでは待つ。
     */
    public static final int HOLD_AFTER_TICKS = 40;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Requirement> requirements() {
        return List.of(Requirement.of("mc_runtime_test"));
    }

    @Override
    public boolean clientOnly() {
        return true;
    }

    @Override
    public boolean shownInClientSettings() {
        return false;
    }
}
