package gomacraft.utility.features.ae2_repo_sort_cache;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 並べ替えの鍵(Mod の id・表示名)を品ごとに 1 回だけ作って使い回す比べ方(ae2_repo_sort_cache)。
 *
 * <p>AE2 の名前順・Mod 順と同じ順序になるように組む:
 * <ul>
 *   <li>名前順: 表示名の文字列を大文字・小文字を無視して比べる。</li>
 *   <li>Mod 順: Mod の id を同じく比べ、同じなら名前順。</li>
 *   <li>降順: ⚠ 鎖の全体を逆にする(Mod の id と名前を別々に逆にしない —— AE2 も鎖ごと逆にしている)。</li>
 * </ul>
 *
 * <p>鍵の置き場は、作った比べ方 1 つ(= 1 回の並べ直し)だけが持つ。並べ直しが済めば捨てるので、言語や
 * リソースが変わっても古い鍵は残らない。品はその並べ直しの中で同じもの(同じ参照)を比べるので、参照で引く。
 *
 * <p>AE2 の型を持たない(JUnit で AE2 と Minecraft を起こさずに確かめるため)。AE2 の並べ方への当てはめは
 * {@code client.Ae2SortKeys}。
 */
public final class SortKeys {
    private SortKeys() {
    }

    /**
     * 鍵を作り置く比べ方を作る。
     *
     * @param byMod     Mod 順なら true、名前順なら false
     * @param ascending 昇順なら true
     * @param modId     品の Mod の id(品ごとに 1 回だけ呼ぶ)
     * @param name      品の表示名の文字列(品ごとに 1 回だけ呼ぶ)
     */
    public static <K> Comparator<K> comparator(
            boolean byMod, boolean ascending, Function<? super K, String> modId, Function<? super K, String> name) {
        Map<K, String> names = new IdentityHashMap<>();
        Comparator<K> byName = Comparator.comparing(key -> names.computeIfAbsent(key, name), String::compareToIgnoreCase);
        Comparator<K> order = byName;
        if (byMod) {
            Map<K, String> mods = new IdentityHashMap<>();
            order = Comparator.<K, String>comparing(key -> mods.computeIfAbsent(key, modId), String::compareToIgnoreCase)
                    .thenComparing(byName);
        }
        return ascending ? order : order.reversed();
    }
}
