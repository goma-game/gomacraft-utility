package gomacraft.utility.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * コンパスの印での絞り込みが、空白を含む Mod の名前・ディメンションの名前に、空白を詰めて打った語でも、名前空間でも
 * 当たること(PR #39 のレビュー —— 語を空白で区切ったので、空白を含む Mod の名前が黙って 0 件になった。EMI と同じく
 * 空白を詰めて打つ形に揃えた —— ユーザの指定)。
 */
class CompassQueryTest {
    private static final Set<CompassQuery.Kind> NATURES = EnumSet.of(CompassQuery.Kind.SOURCE,
            CompassQuery.Kind.TAG, CompassQuery.Kind.DIMENSION);

    @Test
    void compactedNamesAndNamespacesMatch() {
        Map<CompassQuery.Kind, List<String>> biome = Map.of(
                CompassQuery.Kind.NAME, List.of("針葉樹林\nConiferous Forest"),
                CompassQuery.Kind.SOURCE, List.of("Biomes O' Plenty", "biomesoplenty"),
                CompassQuery.Kind.TAG, List.of("#minecraft:is_forest"),
                CompassQuery.Kind.DIMENSION, List.of("minecraft:the_nether", "the nether", "ネザー"),
                CompassQuery.Kind.GROUP, List.of());
        Map<CompassQuery.Kind, List<String>> dungeon = Map.of(
                CompassQuery.Kind.NAME, List.of("Skeleton Dungeon"),
                CompassQuery.Kind.SOURCE, List.of("YUNG's Better Dungeons", "betterdungeons"),
                CompassQuery.Kind.TAG, List.of(),
                CompassQuery.Kind.DIMENSION, List.of("minecraft:overworld", "overworld"),
                CompassQuery.Kind.GROUP, List.of());
        for (String query : List.of("@biomesoplenty", "@biomes", "forest @biomesoplenty", "~thenether", "~nether",
                "~ネザー", "#forest ~nether", "%village forest", "@ forest")) {
            assertTrue(CompassQuery.matches(CompassQuery.parse(query, NATURES), biome::get), query + ": 当たる");
        }
        for (String query : List.of("@yungsbetterdungeons", "@yung's", "@betterdungeons", "@yungs skeleton")) {
            assertTrue(CompassQuery.matches(CompassQuery.parse(query, NATURES), dungeon::get), query + ": 当たる");
        }
        // 受け持つかどうかで当たり方が変わらない(印の語が 1 つだけでも受け持つ)
        assertTrue(CompassQuery.takesOver("@yungsbetterdungeons", NATURES));
        assertFalse(CompassQuery.takesOver("coniferous forest", NATURES), "印の無い語だけはコンパス本来の照合");
        // 使えない印の語・印だけの語を捨てて名前の語だけが残っても受け持つ(本来の照合に任せると、元の文字列のまま
        // 照らして 0 件になる)
        assertTrue(CompassQuery.takesOver("%village forest", NATURES), "使えない印の語を捨てても受け持つ");
        assertTrue(CompassQuery.takesOver("@ forest", NATURES), "印だけの語を捨てても受け持つ");
        assertFalse(CompassQuery.matches(CompassQuery.parse("@biomesoplenty ~overworld", NATURES), biome::get),
                "別のディメンションでは当たらない");
    }
}
