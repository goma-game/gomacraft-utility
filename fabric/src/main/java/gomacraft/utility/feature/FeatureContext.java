package gomacraft.utility.feature;

/**
 * 機能の初期化に渡すもの。
 *
 * <p>fabric 版: forge 版は mod bus と Dist を渡す。Fabric には mod bus が無い(event は Fabric API の静的な口)ので、
 * どちらの側で動いているかだけを渡す。
 *
 * @param client          クライアント(シングルプレイの内蔵サーバを含む)なら true
 * @param dedicatedServer 専用サーバなら true
 */
public record FeatureContext(boolean client, boolean dedicatedServer) {
}
