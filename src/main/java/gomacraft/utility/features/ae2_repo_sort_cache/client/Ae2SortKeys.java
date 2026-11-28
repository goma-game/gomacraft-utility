package gomacraft.utility.features.ae2_repo_sort_cache.client;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import gomacraft.utility.Features;
import gomacraft.utility.features.ae2_repo_sort_cache.Ae2RepoSortCache;
import gomacraft.utility.features.ae2_repo_sort_cache.SortKeys;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * AE2 の並べ方(名前順・Mod 順 × 昇順・降順)を、鍵を作り置く比べ方({@link SortKeys})に置き換える。
 *
 * <p>Mod ID と表示名のキャッシュは {@code updateView()} 間で持続し、言語切り替え・ログアウト・機能 ON/OFF 時に破棄する。
 */
public final class Ae2SortKeys {
    static final int MAX_ENTRIES = 50_000;
    private static final ConcurrentHashMap<AEKey, String> NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<AEKey, String> MODS = new ConcurrentHashMap<>();
    private static volatile Language cachedFor;

    private Ae2SortKeys() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> clearCache());
        Features.registry().onActiveChange(Ae2RepoSortCache.ID, active -> clearCache());
    }

    static void clearCache() {
        NAMES.clear();
        MODS.clear();
    }

    private static void ensureLanguage() {
        Language current = Language.getInstance();
        if (current != cachedFor) {
            synchronized (Ae2SortKeys.class) {
                if (current != cachedFor) {
                    clearCache();
                    cachedFor = current;
                }
            }
        }
    }

    private static String cachedModId(AEKey key) {
        String cached = MODS.get(key);
        if (cached != null) {
            return cached;
        }
        String value = key.getModId();
        if (MODS.size() >= MAX_ENTRIES) {
            MODS.clear();
        }
        MODS.put(key, value);
        return value;
    }

    private static String cachedName(AEKey key) {
        String cached = NAMES.get(key);
        if (cached != null) {
            return cached;
        }
        String value = key.getDisplayName().getString();
        if (NAMES.size() >= MAX_ENTRIES) {
            NAMES.clear();
        }
        NAMES.put(key, value);
        return value;
    }

    /**
     * AE2 の比べ方の代わり。知らない並べ方・向き(AE2 が後で足したもの)は、AE2 の比べ方をそのまま返す。
     *
     * @param original AE2 の比べ方({@code Repo.getKeyComparator} が返したもの)
     */
    public static Comparator<AEKey> comparator(SortOrder order, SortDir dir, Comparator<AEKey> original) {
        boolean byMod;
        if (order == SortOrder.NAME) {
            byMod = false;
        } else if (order == SortOrder.MOD) {
            byMod = true;
        } else {
            return original;
        }
        boolean ascending;
        if (dir == SortDir.ASCENDING) {
            ascending = true;
        } else if (dir == SortDir.DESCENDING) {
            ascending = false;
        } else {
            return original;
        }
        ensureLanguage();
        return SortKeys.order(byMod, ascending, Ae2SortKeys::cachedModId, Ae2SortKeys::cachedName);
    }
}
