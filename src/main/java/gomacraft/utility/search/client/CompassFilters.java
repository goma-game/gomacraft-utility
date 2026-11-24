package gomacraft.utility.search.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * コンパスの検索の、印での絞り込み(goma-org/gomabase#2295 の 5 回目の確かめの B。書き方は {@link gomacraft.utility.search.CompassQuery})。
 *
 * <p>コンパスの {@code processSearchTerm} の頭で呼ばれ(mixin)、この Mod が受け持つ形の検索語なら、結果の一覧を作って
 * 入れ替え、true を返す(コンパス本来の照合は飛ばす)。受け持たない形(印の無い語、{@code @} か {@code $} だけの語)は
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

    /** ディメンションの id({@code minecraft:the_nether})と、{@code _} を空白にした名前({@code the nether})。 */
    static List<String> dimensions(List<ResourceLocation> keys) {
        List<String> out = new ArrayList<>();
        for (ResourceLocation dim : keys) {
            out.add(dim.toString());
            out.add(dim.getPath().replace('_', ' '));
        }
        return out;
    }
}
