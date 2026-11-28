package gomacraft.utility;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureRegistry;
import gomacraft.utility.screenshot.ScreenshotFeatures;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 機能の一覧(ここに足す)と、ゲームの中で 1 つだけ持つ {@link FeatureRegistry}。
 *
 * <p>状態は、最初に問われたとき(mixin の当否を決める時点か、Mod の組み立て)に決まる。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスから写した。一覧は fabric 版へ移した機能だけ。設定の置き場と側の見分けは
 * Fabric Loader から引く。⚠ ここ(と一覧の機能の定義のクラス)では Minecraft のクラスを読まない(mixin の当否を
 * 決める時点で呼ばれる。そこで Minecraft のクラスを読むと、mixin が当たる前に読まれてしまう)。
 */
public final class Features {
    /** 起動の自己検査のときだけ true にする(build.gradle の selftestServer)。配る jar では使わない。 */
    public static final String SELFTEST_PROPERTY = "gomacraft_utility.selftest";

    /**
     * 起動の自己検査のための、わざと壊した機能。どれも自己検査のときだけ一覧に入るので、配った jar の中では
     * mixin も当たらない。壊し方は {@code gomacraft.utility.mixin.<名前>} の mixin を見る。
     */
    public static final List<String> SELFTEST_FEATURES =
            List.of("selftest_broken", "selftest_at", "selftest_missing_class");

    static final Logger LOGGER = LoggerFactory.getLogger(GomacraftUtility.MOD_ID);

    private static FeatureRegistry registry;

    private Features() {
    }

    /** 機能の一覧。 */
    public static List<Feature> catalog(boolean selftest) {
        List<Feature> features = new ArrayList<>();
        // スクショの Discord への投稿(forge 版の段 4 —— goma-org/gomabase#2295)
        features.addAll(ScreenshotFeatures.all());
        if (selftest) {
            for (String id : SELFTEST_FEATURES) {
                features.add(() -> id);
            }
        }
        return features;
    }

    public static synchronized FeatureRegistry registry() {
        if (registry == null) {
            registry = boot();
        }
        return registry;
    }

    /** 機能の中身(mixin の中・event の listener)を守って走らせる({@link FeatureRegistry#guard})。 */
    public static boolean guard(String feature, Runnable body) {
        return registry().guard(feature, body);
    }

    /** 値を返す形の {@link #guard(String, Runnable)}。 */
    public static <T> T guard(String feature, Supplier<T> body, T fallback) {
        return registry().guard(feature, body, fallback);
    }

    /** 専用サーバで動いているか。 */
    public static boolean dedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    private static FeatureRegistry boot() {
        List<Feature> features = catalog(Boolean.getBoolean(SELFTEST_PROPERTY));
        FeatureConfig config;
        try {
            config = FeatureConfig.load(FabricLoader.getInstance().getConfigDir(), features);
            Set<String> unknown = config.unknownKeys(features);
            if (!unknown.isEmpty()) {
                LOGGER.warn("gomacraft_utility: 設定に知らない機能の名前がある(書き間違い?無視する): {}", unknown);
            }
        } catch (Exception e) {
            // 設定を読めなくても起動は止めない。人が切ったつもりの機能を黙って有効にしないよう、全部切る
            LOGGER.warn("gomacraft_utility: 設定を読めないので全部の機能を切る: {}", FeatureRegistry.describe(e));
            config = FeatureConfig.unreadable();
        }
        return FeatureRegistry.evaluate(
                features, FabricModLookup.INSTANCE, config, LOGGER::info, dedicatedServer());
    }
}
