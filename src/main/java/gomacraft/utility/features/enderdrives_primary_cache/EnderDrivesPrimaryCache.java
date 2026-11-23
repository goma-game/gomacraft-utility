package gomacraft.utility.features.enderdrives_primary_cache;

import gomacraft.utility.Features;
import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.Requirement;
import java.util.List;
import java.util.function.Consumer;
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
 * <p>形: 全部を数えた後の答えだけをセルごとに覚え({@link PrimaryCacheLogic})、同じ tick の中でだけ使う(早い返りの道 ——
 * ドライブがつながっていない・grid が無いなど —— は覚えない。元の処理はその道で重なりの印に触らないため)。
 * 答えを変えうることが起きたら、覚えた答えを全部捨てる({@link PrimaryCache#invalidate}): セルをドライブに付けた・外した
 * ({@code DuplicateCellGuard.bind / unbind})、ドライブの中身・つながり・電力が変わった
 * ({@code DriveBlockEntity.onChangeInventory / onMainNodeStateChanged})、grid のノードが増えた・減った
 * ({@code Grid.add / remove} の前と後)。覚えるのはサーバの糸の上だけ。
 *
 * <p>⚠ 効く場面の限り: EnderDrives は入れ・出しが成功するたびに、サーバの次の仕事として grid の全部のドライブのセルを
 * 作り直し、そのとき unbind が覚えた答えを全部捨てる。だから効くのは、断られる問い(入らない・優先でない)が多いとき
 * (Flux の電気の入れなど)が主になる。
 *
 * <p>⚠ 版の限り: 捨てる時点は EnderDrives 1.5.23 と AE2 19.2.18 を読んで決めた。当て先のメソッドが無くなれば FAILED になる
 * (当て先の試験と起動の 1 行)が、版が上がって答えを変える道が増えても、それは赤くならない。
 *
 * <p>専用サーバだけ({@link #serverOnly}。mixin は設定の {@code server} の一覧)。答えの選び方は EnderDrives のまま(写さない)。
 */
public final class EnderDrivesPrimaryCache implements Feature {
    public static final String ID = "enderdrives_primary_cache";

    static final PrimaryCacheLogic LOGIC = new PrimaryCacheLogic(new PrimaryCache(), EnderDrivesPrimaryCache::serverTick);

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

    /** mixin から(頭): 覚えた答え(無ければ null)。当たったら、印の付け直しもこの守りの中でする。 */
    public static Boolean hit(Object cell, Consumer<Boolean> markDuplicate) {
        return Features.guard(ID, () -> LOGIC.hit(cell, markDuplicate), null);
    }

    /** mixin から(全部を数えた後の返り): 元の答えを覚える。 */
    public static void remember(Object cell, boolean primary) {
        Features.guard(ID, () -> LOGIC.remember(cell, primary));
    }

    /** mixin から: 答えを変えうることが起きた。 */
    public static void invalidate() {
        Features.guard(ID, (Runnable) LOGIC::invalidate);
    }
}
