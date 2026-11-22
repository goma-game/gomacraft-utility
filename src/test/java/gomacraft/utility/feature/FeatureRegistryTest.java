package gomacraft.utility.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeatureRegistryTest {
    private static Feature feature(String id, boolean byDefault, Requirement... requirements) {
        return new Feature() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean enabledByDefault() {
                return byDefault;
            }

            @Override
            public List<Requirement> requirements() {
                return List.of(requirements);
            }
        };
    }

    private static ModLookup mods(Map<String, String> versions) {
        return id -> Optional.ofNullable(versions.get(id));
    }

    @Test
    void decidesEachStateFromModsAndConfig() {
        List<String> lines = new ArrayList<>();
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(
                        feature("plain", true),
                        feature("needs_ae2", true, Requirement.of("ae2", "[19.0,20)")),
                        feature("old_ae2", true, Requirement.of("ae2", "[20.0,)")),
                        feature("needs_missing", true, Requirement.of("nothere")),
                        feature("off_by_default", false),
                        feature("turned_off", true),
                        feature("bad_value", true)),
                mods(Map.of("ae2", "19.2.17")),
                FeatureConfig.parse("[features]\nturned_off = false\nbad_value = yes\n"),
                lines::add);

        assertEquals(
                "gomacraft_utility: features plain=on needs_ae2=on"
                        + " old_ae2=off(version: ae2 19.2.17 outside [20.0,])"
                        + " needs_missing=off(mod missing: nothere)"
                        + " off_by_default=off(disabled) turned_off=off(disabled)"
                        + " bad_value=off(config invalid)",
                registry.line());
        assertTrue(registry.isOn("needs_ae2"));
        assertFalse(registry.isOn("old_ae2"));
        assertTrue(lines.isEmpty(), "emit の前には何も出さない");
    }

    @Test
    void configEnablesFeatureThatIsOffByDefault() {
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(feature("opt_in", false)), mods(Map.of()),
                FeatureConfig.parse("[features]\nopt_in = true # 使う\n"), line -> { });
        assertTrue(registry.isOn("opt_in"));
    }

    @Test
    void badVersionRangeIsFailedNotSilentlyOff() {
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(feature("typo", true, Requirement.of("ae2", "19.0,20"))),
                mods(Map.of("ae2", "19.2.17")), FeatureConfig.empty(), line -> { });
        assertEquals(FeatureState.FAILED, registry.status("typo").orElseThrow().state());
    }

    @Test
    void failureAfterEmitIsReportedWithANewLine() {
        List<String> lines = new ArrayList<>();
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(feature("a", true), feature("b", true)), mods(Map.of()), FeatureConfig.empty(), lines::add);
        registry.emit();
        registry.markFailed("b", "InvalidInjectionException: no target (x)\n at y");
        registry.markFailed("b", "二度目は出さない");

        assertEquals(List.of(
                "gomacraft_utility: features a=on b=on",
                "gomacraft_utility: features a=on b=FAILED(InvalidInjectionException: no target [x] at y)"), lines);
        assertFalse(registry.isOn("b"));
    }

    @Test
    void failureOfUnknownFeatureIsNotSwallowed() {
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(), mods(Map.of()), FeatureConfig.empty(), line -> { });
        registry.markFailed("ghost", "boom");
        assertEquals("gomacraft_utility: features unknown_feature=FAILED(ghost: boom)", registry.line());
    }

    @Test
    void initFailureStopsOnlyThatFeature() {
        Feature throwing = new Feature() {
            @Override
            public String id() {
                return "throws";
            }

            @Override
            public void init(FeatureContext context) {
                throw new IllegalStateException("broken");
            }
        };
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(throwing, feature("fine", true)), mods(Map.of()), FeatureConfig.empty(), line -> { });
        registry.initFeatures(new FeatureContext(null, null));
        assertEquals("gomacraft_utility: features throws=FAILED(IllegalStateException: broken) fine=on",
                registry.line());
    }

    @Test
    void noFeaturesRendersNone() {
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(), mods(Map.of()), FeatureConfig.empty(), line -> { });
        assertEquals("gomacraft_utility: features none", registry.line());
    }

    @Test
    void rejectsInvalidOrRepeatedIds() {
        assertThrows(IllegalArgumentException.class, () -> FeatureRegistry.evaluate(
                List.of(feature("Bad-Id", true)), mods(Map.of()), FeatureConfig.empty(), line -> { }));
        assertThrows(IllegalArgumentException.class, () -> FeatureRegistry.evaluate(
                List.of(feature("dup", true), feature("dup", true)), mods(Map.of()), FeatureConfig.empty(),
                line -> { }));
    }
}
