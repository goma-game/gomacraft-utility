package gomacraft.utility.feature;

import java.util.List;

/**
 * 1 つの機能。
 *
 * <p>機能の mixin は {@code gomacraft.utility.mixin.<id>} の包みに置く。mixin の当否は
 * {@link FeatureRegistry} の状態で決まる(有効でなければ当てない)。当て損ねたら、その機能だけが FAILED になる。
 */
public interface Feature {
    /** 機能の名前。設定のキーと起動時の 1 行に出る。{@code [a-z0-9_]+}。 */
    String id();

    /** 設定に書かれていないときに使うか。 */
    default boolean enabledByDefault() {
        return true;
    }

    /** 当てる先の Mod(全部が入っていて版が合うときだけ有効になる)。 */
    default List<Requirement> requirements() {
        return List.of();
    }

    /**
     * 画面にだけ効く機能か。true なら専用サーバでは {@code off(client only)} にする(mixin を client の欄に
     * 置いても、サーバの 1 行に {@code on} と出て、効いていないのに効いて見えるのを避ける)。
     */
    default boolean clientOnly() {
        return false;
    }

    /**
     * 専用サーバにだけ効く機能か({@link #clientOnly} の対)。true ならクライアント(シングルプレイの内蔵サーバを含む)では
     * {@code off(server only)} にする(mixin を server の欄に置いても、クライアントの 1 行に {@code on} と出て、
     * 当たっていないのに効いて見えるのを避ける)。
     */
    default boolean serverOnly() {
        return false;
    }

    /** 有効なときに Mod の組み立ての中で 1 回呼ばれる。例外を投げたら、この機能だけが FAILED になる。 */
    default void init(FeatureContext context) throws Exception {
    }
}
