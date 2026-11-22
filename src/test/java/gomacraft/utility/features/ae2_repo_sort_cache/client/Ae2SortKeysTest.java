package gomacraft.utility.features.ae2_repo_sort_cache.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.menu.me.common.GridInventoryEntry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * 鍵を作り置く比べ方が、組み立ての版の AE2 の比べ方({@code KeySorters} —— 包みの中だけのクラスなので名前で引く)と
 * 同じ並びを作ること(ae2_repo_sort_cache)。⚠ AE2 が並べ方を変えたら、ここが赤くなる。
 *
 * <p>品は、名前と Mod の id だけを持つ偽の {@link AEKey}(Minecraft を起こさずに作れる)。
 */
class Ae2SortKeysTest {
    /** 名前と Mod の id だけを持つ品。Mod の id を作った回数を数える。 */
    static class FakeKey extends AEKey {
        final ResourceLocation id;
        final String name;
        final AtomicInteger modIdCalls;

        FakeKey(String mod, String name, int n, AtomicInteger modIdCalls) {
            this.id = ResourceLocation.fromNamespaceAndPath(mod, "item_" + n);
            this.name = name;
            this.modIdCalls = modIdCalls;
        }

        @Override
        public String getModId() {
            modIdCalls.incrementAndGet();
            return super.getModId();
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(name);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public AEKeyType getType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(HolderLookup.Provider registries) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }

    // ⚠ ResourceLocation の名前空間は小文字だけ(大文字・小文字の違いは名前の側で試す)
    private static final String[] MODS = {"minecraft", "ae2", "mekanism", "allthemodium", "a", "zz"};
    private static final String[] NAMES = {
        "Iron Ingot", "iron ingot", "IRON INGOT", "鉄インゴット", "鉄のインゴット", "Apple", "apple", "Ätherium",
        "atherium", "", "1", "10", "2", "Block of Iron", "ＡＥ２", "ゴマ", "ごま",
    };

    static List<GridInventoryEntry> entries(long seed, int count, AtomicInteger modIdCalls) {
        Random random = new Random(seed);
        List<GridInventoryEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AEKey key = new FakeKey(MODS[random.nextInt(MODS.length)], NAMES[random.nextInt(NAMES.length)], i,
                    modIdCalls);
            entries.add(new GridInventoryEntry(i, key, random.nextInt(100), 0, false));
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    static Comparator<AEKey> ae2(SortOrder order, SortDir dir) throws Exception {
        Method method = Class.forName("appeng.client.gui.me.common.KeySorters")
                .getDeclaredMethod("getComparator", SortOrder.class, SortDir.class);
        method.setAccessible(true);
        return (Comparator<AEKey>) method.invoke(null, order, dir);
    }

    @Test
    void theOrderIsTheSameAsAe2() throws Exception {
        for (SortOrder order : new SortOrder[] {SortOrder.NAME, SortOrder.MOD}) {
            for (SortDir dir : SortDir.values()) {
                for (long seed = 1; seed <= 3; seed++) {
                    List<GridInventoryEntry> expected = entries(seed, 3000, new AtomicInteger());
                    List<GridInventoryEntry> actual = new ArrayList<>(expected);
                    Comparator<AEKey> original = ae2(order, dir);
                    // Repo.getComparator と同じ包み方(品の比べ方で一覧の行を比べる)
                    expected.sort(Comparator.comparing(GridInventoryEntry::getWhat, original));
                    actual.sort(Comparator.comparing(GridInventoryEntry::getWhat,
                            Ae2SortKeys.comparator(order, dir, original)));
                    assertEquals(expected, actual, order + " " + dir + " seed=" + seed);
                }
            }
        }
    }

    @Test
    void otherOrdersAreLeftToAe2() {
        Comparator<AEKey> original = (a, b) -> 0;
        assertSame(original, Ae2SortKeys.comparator(SortOrder.AMOUNT, SortDir.ASCENDING, original));
    }

    @Test
    void theModIdIsBuiltOncePerKey() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<GridInventoryEntry> list = entries(9, 5000, calls);
        list.sort(Comparator.comparing(GridInventoryEntry::getWhat,
                Ae2SortKeys.comparator(SortOrder.MOD, SortDir.ASCENDING, ae2(SortOrder.MOD, SortDir.ASCENDING))));
        assertTrue(calls.get() <= list.size(), "Mod の id を品の数より多く作った: " + calls.get());
    }
}
