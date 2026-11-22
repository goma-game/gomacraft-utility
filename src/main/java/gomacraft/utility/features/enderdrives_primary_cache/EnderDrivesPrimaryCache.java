package gomacraft.utility.features.enderdrives_primary_cache;

import gomacraft.utility.Features;
import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.Requirement;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * EnderDrives の「このセルがその grid で代表か」({@code DuplicateCellGuard.isPrimary})の答えを、同じ tick の中で使い回す
 * (goma-org/gomabase#2333)。
 *
 * <p>なぜ: 本番の spark で、isPrimary が tick の 9.4% を使っていた。EnderDrives のテープのセル({@code TapeDiskInventory})が
 * 入れ・出し・優先の判定・在庫の集計のたびに呼び、1 回ごとに grid の全部のドライブとセルを数え直す
 * ({@code Grid.getMachines} の集合を作り、各ドライブの各スロットのセルを見る)。Flux Networks の電気の入れのように
 * 1 tick に何度も入れる道で、同じ答えを何度も作っていた。
 *
 * <p>形: 答えはセルごとに覚え、同じ tick の中でだけ使う。答えを変えうることが起きたら、覚えた答えを全部捨てる
 * ({@link PrimaryCache#invalidate}): セルをドライブに付けた・外した({@code DuplicateCellGuard.bind / unbind})、
 * ドライブの中身・つながり・電力が変わった({@code DriveBlockEntity.onChangeInventory / onMainNodeStateChanged})、
 * grid のノードが増えた・減った({@code Grid.add / remove} —— grid の組み直しもこの 2 つを通る)。
 * 覚えるのはサーバの糸の上だけ(ほかの糸からの問いは、元どおり数え直す)。
 *
 * <p>専用サーバだけ({@link #serverOnly}。mixin は設定の {@code server} の一覧)。答えの選び方(どのセルを代表にするか)は
 * EnderDrives のまま(写さない。元の答えを覚えるだけ)。
 */
public final class EnderDrivesPrimaryCache implements Feature {
    public static final String ID = "enderdrives_primary_cache";

    static final PrimaryCache CACHE = new PrimaryCache();

    @Override
    public String id() {
        return ID;
    }

    /** isPrimary の中身(数え方)に当てるので、系列の版に限る。 */
    @Override
    public List<Requirement> requirements() {
        return List.of(Requirement.of("enderdrives", "[1.5,1.6)"), Requirement.of("ae2", "[19.2,19.3)"));
    }

    @Override
    public boolean serverOnly() {
        return true;
    }

    /** 今のサーバの tick(サーバの糸の上でなければ -1 = 覚えない)。 */
    private static long serverTick() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return -1;
        }
        return server.getTickCount();
    }

    /** mixin から: 覚えた答え(無ければ null)。 */
    public static Boolean cached(Object cell) {
        return Features.guard(ID, () -> {
            long tick = serverTick();
            return tick < 0 ? null : CACHE.get(cell, tick);
        }, null);
    }

    /** mixin から: 元の答えを覚える。 */
    public static void remember(Object cell, boolean primary) {
        Features.guard(ID, () -> {
            long tick = serverTick();
            if (tick >= 0) {
                CACHE.put(cell, tick, primary);
            }
        });
    }

    /** mixin から: 答えを変えうることが起きた。 */
    public static void invalidate() {
        Features.guard(ID, (Runnable) CACHE::invalidate);
    }

    /** 試験用: 元の答えの代わり(覚えた答えが元の答えと同じかを見る)。 */
    static boolean answer(Object cell, long tick, BooleanSupplier original) {
        Boolean hit = CACHE.get(cell, tick);
        if (hit != null) {
            return hit;
        }
        boolean value = original.getAsBoolean();
        CACHE.put(cell, tick, value);
        return value;
    }
}
