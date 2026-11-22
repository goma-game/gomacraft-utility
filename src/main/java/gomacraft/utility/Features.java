package gomacraft.utility;

import gomacraft.utility.feature.Feature;
import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureRegistry;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 機能の一覧(ここに足す)と、ゲームの中で 1 つだけ持つ {@link FeatureRegistry}。
 *
 * <p>状態は、最初に問われたとき(mixin の当否を決める時点か、Mod の組み立て)に決まる。
 */
public final class Features {
    /** 起動の自己検査のときだけ true にする(build.gradle の gameTestServer)。配る jar では使わない。 */
    public static final String SELFTEST_PROPERTY = "gomacraft_utility.selftest";

    static final Logger LOGGER = LoggerFactory.getLogger(GomacraftUtility.MOD_ID);

    private static FeatureRegistry registry;

    private Features() {
    }

    /** 機能の一覧。 */
    public static List<Feature> catalog(boolean selftest) {
        List<Feature> features = new ArrayList<>();
        // 段 0 では機能の中身はまだ無い(枠だけ)。機能はここに足す。
        if (selftest) {
            features.add(new SelftestBrokenFeature());
        }
        return features;
    }

    public static synchronized FeatureRegistry registry() {
        if (registry == null) {
            registry = boot();
        }
        return registry;
    }

    private static FeatureRegistry boot() {
        List<Feature> features = catalog(Boolean.getBoolean(SELFTEST_PROPERTY));
        FeatureConfig config;
        try {
            config = FeatureConfig.load(FMLPaths.CONFIGDIR.get(), features);
        } catch (Exception e) {
            // 設定を読めなくても起動は止めない(既定の値で動き、そのことを出す)
            LOGGER.warn("gomacraft_utility: 設定を読めないので既定の値で動く: {}", FeatureRegistry.describe(e));
            config = FeatureConfig.empty();
        }
        return FeatureRegistry.evaluate(features, FmlModLookup.INSTANCE, config, LOGGER::info);
    }

    /**
     * 起動の自己検査のための、わざと壊した機能。当て先の無い mixin
     * ({@code gomacraft.utility.mixin.selftest_broken})を持ち、当て損ねてもこの機能だけが FAILED になり、
     * 起動が続くことを gametest が確かめる。自己検査の外では一覧に入らないので、mixin も当たらない。
     */
    static final class SelftestBrokenFeature implements Feature {
        @Override
        public String id() {
            return "selftest_broken";
        }
    }
}
