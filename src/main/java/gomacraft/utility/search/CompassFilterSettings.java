package gomacraft.utility.search;

import gomacraft.utility.settings.ClientSettings;

/**
 * コンパスの検索の、印での絞り込み({@link CompassQuery})の入り切り(既定は入)。Minecraft に依らない(設定の画面の
 * 訳の試験が、宣言した項目をここから読む)。節はそれぞれのコンパスの検索の機能(照合を差し替える mixin がその機能に在る)。
 */
public final class CompassFilterSettings {
    public static final ClientSettings.Bool NATURES =
            new ClientSettings.Bool(SearchFeatures.NATURESCOMPASS_SEARCH, "filters", true);
    public static final ClientSettings.Bool EXPLORERS =
            new ClientSettings.Bool(SearchFeatures.EXPLORERSCOMPASS_SEARCH, "filters", true);

    private CompassFilterSettings() {
    }
}
