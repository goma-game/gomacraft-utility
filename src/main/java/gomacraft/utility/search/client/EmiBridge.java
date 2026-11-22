package gomacraft.utility.search.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;

/**
 * EMI の公開の API だけを使う部分。EMI が入っているときだけ呼ぶ(呼ぶ側が {@code ModList} で確かめる。
 * このクラスを読むと EMI のクラスが要る)。
 */
final class EmiBridge {
    private EmiBridge() {
    }

    static boolean isSearchFocused() {
        return EmiApi.isSearchFocused();
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
