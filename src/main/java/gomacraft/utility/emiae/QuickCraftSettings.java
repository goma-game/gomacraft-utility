package gomacraft.utility.emiae;

import gomacraft.utility.settings.ClientSettings;

/**
 * EMI のアイテムを取り出す・作る機能({@link EmiAeFeatures#QUICK_CRAFT})の手元の設定。Minecraft に依らない
 * (設定の画面の訳の試験が、宣言した項目をここから読む —— 画面のクラスの {@code QuickCraft} は KeyMapping を持つので読めない)。
 */
public final class QuickCraftSettings {
    /**
     * Alt + クリック・Alt + Shift + クリックを使うか(既定は切 —— 使う人が少ない。goma-org/gomabase#2295 の
     * 2 回目の確かめの 4)。切のあいだは、その操作を EMI へそのまま通す。パターンの無いアイテムを作業台のレシピで作る
     * のもこの操作からなので、切のあいだは動かない。中クリックはこの設定に依らない。
     */
    public static final ClientSettings.Bool ALT_CLICK =
            new ClientSettings.Bool(EmiAeFeatures.QUICK_CRAFT, "alt_click", false);

    /** Alt + クリックで作るとき、計画の画面も飛ばして一気に作るか(既定は切 —— 量をその場で変えられないため)。 */
    public static final ClientSettings.Bool SKIP_CONFIRM =
            new ClientSettings.Bool(EmiAeFeatures.QUICK_CRAFT, "skip_confirm", false);

    private QuickCraftSettings() {
    }
}
