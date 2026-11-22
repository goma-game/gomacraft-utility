package gomacraft.utility.search.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;

/**
 * EMI を使う部分(公開の API の EmiApi と、検索欄の位置だけは EMI の中の public の欄 —— {@link #isMouseOverSearch})。
 * EMI が入っているときだけ呼ぶ(呼ぶ側が {@code ModList} で確かめる。このクラスを読むと EMI のクラスが要る)。
 */
final class EmiBridge {
    private EmiBridge() {
    }

    static boolean isSearchFocused() {
        return EmiApi.isSearchFocused();
    }

    /**
     * マウスが EMI の検索欄の上か。⚠ EMI の公開の口(EmiApi)には検索欄の位置が無いので、EMI の中の
     * {@code EmiScreenManager.search}(public static の EditBox)を読む。EMI が名前を変えれば例外になり、F のキーの機能
     * (ae2_search_key)が FAILED になる(黙って効かなくはならない)。
     */
    static boolean isMouseOverSearch(double mouseX, double mouseY) {
        var search = dev.emi.emi.screen.EmiScreenManager.search;
        return search != null && search.isVisible() && search.isMouseOver(mouseX, mouseY);
    }

    static String getSearchText() {
        return EmiApi.getSearchText();
    }

    static void setSearchText(String text) {
        EmiApi.setSearchText(text);
    }

    static String hoveredSearchText(boolean modId) {
        var interaction = EmiApi.getHoveredStack(true);
        if (interaction == null || interaction.isEmpty()) {
            return null;
        }
        var stacks = interaction.getStack().getEmiStacks();
        if (stacks.isEmpty()) {
            return null;
        }
        EmiStack first = stacks.get(0);
        if (first.isEmpty()) {
            return null;
        }
        return modId ? "@" + first.getId().getNamespace() : first.getName().getString();
    }
}
