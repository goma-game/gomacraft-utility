package gomacraft.utility.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 機能の一覧と、その状態。
 *
 * <p>状態は 1 回だけ決める(mixin の当否を決める時点で、対象の Mod の有無と版・設定から)。その後に変わるのは
 * 「壊れた」(FAILED)への変化だけ。起動時の 1 行を出した後に変われば、同じ形の行をもう一度出す。
 *
 * <p>起動のときに有効になった機能は、遊んでいる間に止めて、また動かせる({@link #setActive} —— 設定の画面から)。
 * 止めている間は {@link #isOn} が偽になり、守り({@link #guard})の中身は走らない。止めることは状態ではないので、
 * 起動時の 1 行({@link #line})にも {@link #status} にも出さない(起動のときに決まった {@code on} のまま —— gomabase の
 * 起動の検証との約束を変えない)。止めても FAILED にはならず、動かし直しても FAILED は消えない。
 * 起動のときに有効でなかった機能は mixin が当たっていないので、動かすには起動し直す。
 *
 * <p>⚠ {@link #isOn} の答えを覚えて使い回さない(止めたのが効かなくなる)。毎回問う。
 */
public final class FeatureRegistry {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern RANGE = Pattern.compile("[\\[(][^\\[\\]()]*[\\])](,[\\[(][^\\[\\]()]*[\\])])*");
    private static final Logger LOGGER = LoggerFactory.getLogger("gomacraft_utility");

    private final List<Feature> features;
    private final Map<String, FeatureStatus> statuses;
    private final Consumer<String> sink;
    /** 起動の後に止めた機能(起動のときに有効になったものだけ)。 */
    private final Set<String> suspended = new HashSet<>();
    /** 止めた・動かし直したときに呼ぶもの(機能 → 呼ぶもの)。 */
    private final Map<String, List<Consumer<Boolean>>> hooks = new LinkedHashMap<>();
    /** 設定で入にして起動し直せば有効になる機能({@link #enableableByRestart})。 */
    private final Set<String> restartable;
    private boolean emitted;

    private FeatureRegistry(List<Feature> features, Map<String, FeatureStatus> statuses, Set<String> restartable,
            Consumer<String> sink) {
        this.features = features;
        this.statuses = statuses;
        this.restartable = restartable;
        this.sink = sink;
    }

    /** 機能の一覧の状態を決める(Minecraft に依らない。試験はここを見る)。 */
    public static FeatureRegistry evaluate(
            List<? extends Feature> features, ModLookup mods, FeatureConfig config, Consumer<String> sink) {
        return evaluate(features, mods, config, sink, false);
    }

    /**
     * {@code dedicatedServer} が真なら、画面にだけ効く機能({@link Feature#clientOnly})を切る。偽(クライアント)なら、
     * 専用サーバにだけ効く機能({@link Feature#serverOnly})を切る。
     */
    public static FeatureRegistry evaluate(
            List<? extends Feature> features, ModLookup mods, FeatureConfig config, Consumer<String> sink,
            boolean dedicatedServer) {
        Set<String> seen = new HashSet<>();
        Map<String, FeatureStatus> statuses = new LinkedHashMap<>();
        Set<String> restartable = new HashSet<>();
        for (Feature f : features) {
            if (!ID.matcher(f.id()).matches() || !seen.add(f.id())) {
                throw new IllegalArgumentException("機能の名前が不正か重なっている: " + f.id());
            }
            FeatureStatus status = decide(f, mods, config);
            // 設定で切ってあった(値が読めなかった)機能が、入にして起動し直せば有効になるか(要る Mod と版、効く側も見る)
            boolean byConfig = status.state() == FeatureState.OFF && status.reason() != null
                    && (status.reason().equals("disabled") || status.reason().startsWith("config "));
            if (byConfig && decide(f, mods, FeatureConfig.parse("[features]\n" + f.id() + " = true\n")).state()
                    == FeatureState.ON && !(dedicatedServer ? f.clientOnly() : f.serverOnly())) {
                restartable.add(f.id());
            }
            if (dedicatedServer && f.clientOnly() && status.state() == FeatureState.ON) {
                status = new FeatureStatus(f.id(), FeatureState.OFF, "client only");
            }
            if (!dedicatedServer && f.serverOnly() && status.state() == FeatureState.ON) {
                status = new FeatureStatus(f.id(), FeatureState.OFF, "server only");
            }
            statuses.put(f.id(), status);
        }
        return new FeatureRegistry(List.copyOf(features), statuses, Set.copyOf(restartable), sink);
    }

    private static FeatureStatus decide(Feature f, ModLookup mods, FeatureConfig config) {
        Optional<FeatureConfig.Value> value = config.value(f.id());
        if (value.isPresent() && value.get() == FeatureConfig.Value.UNREADABLE) {
            return new FeatureStatus(f.id(), FeatureState.OFF, "config unreadable");
        }
        if (value.isPresent() && value.get() == FeatureConfig.Value.INVALID) {
            return new FeatureStatus(f.id(), FeatureState.OFF, "config invalid");
        }
        boolean enabled = value.map(v -> v == FeatureConfig.Value.TRUE).orElse(f.enabledByDefault());
        if (!enabled) {
            return new FeatureStatus(f.id(), FeatureState.OFF, "disabled");
        }
        for (Requirement r : f.requirements()) {
            Optional<String> version = mods.version(r.modId());
            if (version.isEmpty()) {
                return new FeatureStatus(f.id(), FeatureState.OFF, "mod missing: " + r.modId());
            }
            if (r.versionRange() != null) {
                VersionRange range;
                try {
                    // 括弧の無い書き方(例: "19.0")は Maven では「推奨の版」になり、どの版でも通ってしまう。
                    // 範囲は括弧で書かせる
                    if (!RANGE.matcher(r.versionRange()).matches()) {
                        throw new InvalidVersionSpecificationException("not a bracketed range");
                    }
                    range = VersionRange.createFromVersionSpec(r.versionRange());
                } catch (InvalidVersionSpecificationException e) {
                    // 機能の定義の誤り。黙って有効・無効にせず、壊れたと出す
                    return new FeatureStatus(f.id(), FeatureState.FAILED, "bad version range: " + r.versionRange());
                }
                if (!range.containsVersion(new DefaultArtifactVersion(version.get()))) {
                    return new FeatureStatus(f.id(), FeatureState.OFF,
                            "version: " + r.modId() + " " + version.get() + " outside " + r.versionRange());
                }
            }
        }
        return new FeatureStatus(f.id(), FeatureState.ON, null);
    }

    public synchronized Optional<FeatureStatus> status(String id) {
        return Optional.ofNullable(statuses.get(id));
    }

    /** 今動いているか(起動のときに有効になり、壊れておらず、止めていない)。機能の中身はこれで分ける。 */
    public synchronized boolean isOn(String id) {
        return isLoaded(id) && !suspended.contains(id);
    }

    /**
     * 起動のときに有効になり、壊れていないか(止めているかは見ない)。mixin の当否・初期化と、後から何度も呼ばれる登録
     * (EMI の plugin・クライアントのコマンドなど。止めている間に呼ばれても、動かし直したら効くように)に使う。
     */
    public synchronized boolean isLoaded(String id) {
        FeatureStatus s = statuses.get(id);
        return s != null && s.state() == FeatureState.ON;
    }

    /** 起動の後に {@link #setActive} で止めているか。 */
    public synchronized boolean isSuspended(String id) {
        return isLoaded(id) && suspended.contains(id);
    }

    /** 起動時または実行時にエラーで FAILED になっているか。 */
    public synchronized boolean isFailed(String id) {
        FeatureStatus s = statuses.get(id);
        return s != null && s.state() == FeatureState.FAILED;
    }

    /**
     * 機能を止める・動かし直す(設定の画面から)。効くのは、起動のときに有効になった機能だけ。
     *
     * @return その場で効いたら true。起動のときに有効でなかった(mixin が当たっていない)か壊れた機能は false
     */
    public boolean setActive(String id, boolean active) {
        List<Consumer<Boolean>> run;
        synchronized (this) {
            if (!isLoaded(id)) {
                return false;
            }
            boolean changed = active ? suspended.remove(id) : suspended.add(id);
            if (!changed) {
                return true;
            }
            run = List.copyOf(hooks.getOrDefault(id, List.of()));
        }
        for (Consumer<Boolean> hook : run) {
            try {
                hook.accept(active);
            } catch (Throwable t) {
                // 片付けの不具合。止めること自体は壊れたことではないので FAILED にせず、ログに残す
                LOGGER.warn("gomacraft_utility: {} を{}ときの片付けが失敗した: {}", id,
                        active ? "動かし直した" : "止めた", describe(t));
            }
        }
        return true;
    }

    /**
     * 止めた・動かし直したときに呼ぶものを足す(引数は、動かし直したら true)。止めている間に古くなった作り置きを捨てる・
     * 途中の操作の待ちを捨てる、などに使う。呼ぶのは {@link #setActive} を呼んだ thread(設定の画面 = 描画の thread)。
     */
    public synchronized void onActiveChange(String id, Consumer<Boolean> hook) {
        hooks.computeIfAbsent(id, k -> new ArrayList<>()).add(hook);
    }

    /**
     * 設定で入にして起動し直せば有効になりうるか(起動のときに、切ってあった・設定の値が読めなかった)。要る Mod が無い・
     * 版が合わない・壊れた機能は、起動し直しても有効にならないので偽。
     */
    public synchronized boolean enableableByRestart(String id) {
        FeatureStatus s = statuses.get(id);
        return s != null && s.state() == FeatureState.OFF && restartable.contains(id);
    }

    public synchronized List<FeatureStatus> statuses() {
        return List.copyOf(statuses.values());
    }

    /** 有効な機能を初期化する。例外を投げた機能だけが FAILED になり、ほかは続ける。 */
    public void initFeatures(FeatureContext context) {
        for (Feature f : features) {
            if (!isLoaded(f.id())) {
                continue;
            }
            try {
                f.init(context);
            } catch (Throwable t) {
                markFailed(f.id(), describe(t));
            }
        }
    }

    /**
     * 機能を壊れたと記す。知らない名前は {@code unknown_<名前>} として記す(壊れたことを黙らない。名前ごとに分ける)。
     * 起動時の 1 行を出した後なら、もう一度出す。
     */
    public synchronized void markFailed(String id, String reason) {
        String key = statuses.containsKey(id) ? id : unknownKey(id);
        FeatureStatus before = statuses.get(key);
        if (before != null && before.state() == FeatureState.FAILED) {
            return;
        }
        suspended.remove(key);
        statuses.put(key, new FeatureStatus(key, FeatureState.FAILED, reason));
        if (emitted) {
            sink.accept(line());
        }
    }

    private static String unknownKey(String id) {
        String safe = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return "unknown_" + (safe.isEmpty() ? "feature" : safe);
    }

    /**
     * 機能の中身(mixin の中・event の listener)を守って走らせる。有効でなければ何もしない。例外を投げたら
     * その機能を FAILED にし、以後はその機能のどの部分も走らない(FAILED の機能の残りの mixin・listener を止める)。
     *
     * @return 走らせて例外が無ければ true
     */
    public boolean guard(String id, Runnable body) {
        if (!isOn(id)) {
            return false;
        }
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            markFailed(id, describe(t));
            return false;
        }
    }

    /** {@link #guard(String, Runnable)} の値を返す形。有効でないか例外なら {@code fallback}。 */
    public <T> T guard(String id, java.util.function.Supplier<T> body, T fallback) {
        if (!isOn(id)) {
            return fallback;
        }
        try {
            return body.get();
        } catch (Throwable t) {
            markFailed(id, describe(t));
            return fallback;
        }
    }

    /**
     * {@link #guard} の、止めていても走らせる形({@link #isLoaded})。後から何度も呼ばれる登録の口(EMI の plugin・
     * クライアントのコマンド)に使う。登録したものの中身は {@link #guard} で分ける。
     */
    public boolean guardLoaded(String id, Runnable body) {
        if (!isLoaded(id)) {
            return false;
        }
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            markFailed(id, describe(t));
            return false;
        }
    }

    /** 起動時の 1 行を出す。 */
    public synchronized void emit() {
        emitted = true;
        sink.accept(line());
    }

    public synchronized String line() {
        return StatusLine.render(new ArrayList<>(statuses.values()));
    }

    public static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + message;
    }
}
