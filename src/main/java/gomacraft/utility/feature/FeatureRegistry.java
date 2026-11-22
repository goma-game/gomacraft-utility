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

/**
 * 機能の一覧と、その状態。
 *
 * <p>状態は 1 回だけ決める(mixin の当否を決める時点で、対象の Mod の有無と版・設定から)。その後に変わるのは
 * 「壊れた」(FAILED)への変化だけ。起動時の 1 行を出した後に変われば、同じ形の行をもう一度出す。
 */
public final class FeatureRegistry {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern RANGE = Pattern.compile("[\\[(][^\\[\\]()]*[\\])](,[\\[(][^\\[\\]()]*[\\])])*");

    private final List<Feature> features;
    private final Map<String, FeatureStatus> statuses;
    private final Consumer<String> sink;
    private boolean emitted;

    private FeatureRegistry(List<Feature> features, Map<String, FeatureStatus> statuses, Consumer<String> sink) {
        this.features = features;
        this.statuses = statuses;
        this.sink = sink;
    }

    /** 機能の一覧の状態を決める(Minecraft に依らない。試験はここを見る)。 */
    public static FeatureRegistry evaluate(
            List<? extends Feature> features, ModLookup mods, FeatureConfig config, Consumer<String> sink) {
        Set<String> seen = new HashSet<>();
        Map<String, FeatureStatus> statuses = new LinkedHashMap<>();
        for (Feature f : features) {
            if (!ID.matcher(f.id()).matches() || !seen.add(f.id())) {
                throw new IllegalArgumentException("機能の名前が不正か重なっている: " + f.id());
            }
            statuses.put(f.id(), decide(f, mods, config));
        }
        return new FeatureRegistry(List.copyOf(features), statuses, sink);
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

    public synchronized boolean isOn(String id) {
        FeatureStatus s = statuses.get(id);
        return s != null && s.state() == FeatureState.ON;
    }

    public synchronized List<FeatureStatus> statuses() {
        return List.copyOf(statuses.values());
    }

    /** 有効な機能を初期化する。例外を投げた機能だけが FAILED になり、ほかは続ける。 */
    public void initFeatures(FeatureContext context) {
        for (Feature f : features) {
            if (!isOn(f.id())) {
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
