package gomacraft.utility.search.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

/**
 * コンパスの検索の、印での絞り込み(goma-org/gomabase#2295 の 5 回目の確かめの B。書き方は {@link gomacraft.utility.search.CompassQuery})。
 *
 * <p>コンパスの {@code processSearchTerm} の頭で呼ばれ(mixin)、この Mod が受け持つ形の検索語なら、結果の一覧を作って
 * 入れ替え、true を返す(コンパス本来の照合は飛ばす)。印の無い語だけの検索語は
 * false で本来の照合に任せる。照合に使う値はどれも、コンパスがクライアントへ届けているもの:
 * <ul>
 *   <li>Nature's Compass: 名前(英語名を足したもの)・ソース・タグ(バイオームの登録はクライアントにも在る)・
 *       ディメンション({@code NaturesCompass.dimensionKeysForAllowedBiomeKeys})。グループは無い。</li>
 *   <li>Explorer's Compass: 名前・ソース・グループ({@code ExplorersCompass.structureKeysToTypeKeys})・ディメンション。
 *       ⚠ タグは無い(構造物の登録はサーバにしか無く、タグがクライアントへ届かない)。</li>
 * </ul>
 */
public final class CompassFilters {
    private CompassFilters() {
    }

    static String namespace(ResourceLocation key) {
        return key == null ? null : key.getNamespace();
    }

    /**
     * ディメンションの id({@code minecraft:the_nether})・{@code _} を空白にした名前({@code the nether})・訳された表示名と
     * その英語(コンパスが一覧に出すのと同じ {@code dimension.<名前空間>.<名前>} の訳。在れば)。
     */
    static List<String> dimensions(List<ResourceLocation> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (ResourceLocation dim : keys) {
            if (dim == null) {
                continue;
            }
            out.add(dim.toString());
            out.add(dim.getPath().replace('_', ' '));
            String key = Util.makeDescriptionId("dimension", dim);
            if (I18n.exists(key)) {
                out.add(I18n.get(key));
            }
            EnglishNames.lookup(key).ifPresent(out::add);
        }
        return out;
    }
}
