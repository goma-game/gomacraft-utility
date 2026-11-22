package gomacraft.utility.features.ae2_repo_sort_cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 鍵を作り置く比べ方が、作り置かない比べ方(AE2 の {@code KeySorters} と同じ組み方)と同じ並びを作ること
 * (ae2_repo_sort_cache)。AE2 の本物の比べ方との突き合わせは {@code Ae2SortKeysTest}。
 */
class SortKeysTest {
    /** 品の代わり。⚠ 同じ中身の品が別の参照で並ぶこともある(参照で引く置き場が、同じ中身をまとめないこと)。 */
    static final class Key {
        final String mod;
        final String name;
        final int id;

        Key(String mod, String name, int id) {
            this.mod = mod;
            this.name = name;
            this.id = id;
        }

        @Override
        public String toString() {
            return id + ":" + mod + ":" + name;
        }
    }

    private static final String[] MODS = {"minecraft", "Minecraft", "ae2", "AE2", "mekanism", "allthemodium", "zz"};
    private static final String[] NAMES = {
        "Iron Ingot", "iron ingot", "IRON INGOT", "Iron ingot ", "鉄インゴット", "鉄のインゴット", "Apple", "apple",
        "Ätherium", "atherium", "", "1", "10", "2", "Block of Iron", "block of iron", "ＡＥ２", "ゴマ", "ごま",
    };

    static List<Key> keys(long seed, int count) {
        Random random = new Random(seed);
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(new Key(MODS[random.nextInt(MODS.length)], NAMES[random.nextInt(NAMES.length)], i));
        }
        return keys;
    }

    /** 作り置かない比べ方(AE2 の KeySorters と同じ組み方 —— 降順は鎖ごと逆)。 */
    static Comparator<Key> reference(boolean byMod, boolean ascending) {
        Comparator<Key> byName = Comparator.comparing((Key key) -> key.name, String::compareToIgnoreCase);
        Comparator<Key> order = byMod
                ? Comparator.comparing((Key key) -> key.mod, String::compareToIgnoreCase).thenComparing(byName)
                : byName;
        return ascending ? order : order.reversed();
    }

    @Test
    void theOrderIsTheSameAsWithoutTheCache() {
        for (boolean byMod : new boolean[] {false, true}) {
            for (boolean ascending : new boolean[] {true, false}) {
                for (long seed = 1; seed <= 5; seed++) {
                    List<Key> expected = keys(seed, 3000);
                    List<Key> actual = new ArrayList<>(expected);
                    // List.sort は安定(同じ鍵の品は元の順のまま)なので、比べの結果が同じなら並びは品の参照まで同じになる
                    expected.sort(reference(byMod, ascending));
                    actual.sort(SortKeys.comparator(byMod, ascending, (Key key) -> key.mod, (Key key) -> key.name));
                    assertEquals(expected, actual, "byMod=" + byMod + " ascending=" + ascending + " seed=" + seed);
                }
            }
        }
    }

    @Test
    void eachKeyIsBuiltOnlyOncePerSort() {
        List<Key> list = keys(7, 5000);
        AtomicInteger names = new AtomicInteger();
        AtomicInteger mods = new AtomicInteger();
        list.sort(SortKeys.comparator(true, false,
                (Key key) -> {
                    mods.incrementAndGet();
                    return key.mod;
                },
                (Key key) -> {
                    names.incrementAndGet();
                    return key.name;
                }));
        assertTrue(mods.get() <= list.size(), "Mod の id を品の数より多く作った: " + mods.get());
        assertTrue(names.get() <= list.size(), "表示名を品の数より多く作った: " + names.get());
    }
}
