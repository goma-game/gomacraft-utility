package gomacraft.utility.settings;

/**
 * ゲームの中で 1 つだけ持つ、手元の設定({@link ClientSettings})。
 *
 * <p>機能は {@code init} の中で項目を {@link ClientSettings#register} し、Mod の組み立ての最後に
 * {@code GomacraftUtility} がファイルを読む(画面の側だけ。専用サーバは手元の設定を持たない)。
 */
public final class Settings {
    private static final ClientSettings CLIENT = new ClientSettings();

    private Settings() {
    }

    public static ClientSettings client() {
        return CLIENT;
    }
}
