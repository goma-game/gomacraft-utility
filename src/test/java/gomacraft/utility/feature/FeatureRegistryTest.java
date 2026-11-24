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
                        + " old_ae2=off(version: ae2 19.2.17 outside [20.0,})"
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
                "gomacraft_utility: features a=on b=FAILED(InvalidInjectionException: no target {x} at y)"), lines);
        assertFalse(registry.isOn("b"));
    }

    @Test
    void failureOfUnknownFeatureIsNotSwallowed() {
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(), mods(Map.of()), FeatureConfig.empty(), line -> { });
        registry.markFailed("ghost", "boom");
        registry.markFailed("Other-One", "bang");
        assertEquals("gomacraft_utility: features unknown_ghost=FAILED(boom) unknown_other_one=FAILED(bang)",
                registry.line());
    }

    @Test
    void unreadableConfigTurnsEverythingOff() {
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(feature("a", true), feature("b", false)), mods(Map.of()), FeatureConfig.unreadable(),
                line -> { });
        assertEquals("gomacraft_utility: features a=off(config unreadable) b=off(config unreadable)",
                registry.line());
    }

    @Test
    void guardRunsOnlyEnabledFeaturesAndStopsThemAfterAnException() {
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(feature("live", true), feature("off", false)), mods(Map.of()), FeatureConfig.empty(),
                line -> { });
        List<String> ran = new ArrayList<>();
        assertTrue(registry.guard("live", () -> ran.add("live-1")));
        assertFalse(registry.guard("off", () -> ran.add("off")));
        assertFalse(registry.guard("live", () -> {
            throw new IllegalStateException("boom");
        }));
        assertFalse(registry.guard("live", () -> ran.add("live-2")), "FAILED の後は走らない");
        assertEquals("fallback", registry.guard("live", () -> "value", "fallback"));
        assertEquals(List.of("live-1"), ran);
        assertEquals(FeatureState.FAILED, registry.status("live").orElseThrow().state());
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
    void serverOnlyFeaturesAreOffOnTheClient() {
        Feature serverFix = new Feature() {
            @Override
            public String id() {
                return "server_fix";
            }

            @Override
            public boolean serverOnly() {
                return true;
            }
        };
        assertEquals("gomacraft_utility: features server_fix=off(server only)",
                FeatureRegistry.evaluate(List.of(serverFix), mods(Map.of()), FeatureConfig.empty(), line -> { }, false)
                        .line());
        assertEquals("gomacraft_utility: features server_fix=on",
                FeatureRegistry.evaluate(List.of(serverFix), mods(Map.of()), FeatureConfig.empty(), line -> { }, true)
                        .line());
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

    /**
     * 設定の画面で止める・動かし直すのは、起動の 1 行(gomabase の起動の検証が読む)を変えず、FAILED にもしない。
     * 起動のときに切ってあった機能は、その場では動かない(mixin が当たっていない)。
     */
    @Test
    void suspendingKeepsTheStatusLineAndNeverMarksFailed() {
        List<String> lines = new ArrayList<>();
        FeatureRegistry registry = FeatureRegistry.evaluate(
                List.of(feature("live", true), feature("cut", true), feature("broken", true),
                        feature("cut_missing", true, Requirement.of("nothere"))), mods(Map.of()),
                FeatureConfig.parse("[features]\ncut = false\ncut_missing = false\n"), lines::add);
        registry.emit();
        String bootLine = registry.line();
        List<Boolean> hook = new ArrayList<>();
        registry.onActiveChange("live", hook::add);

        assertTrue(registry.setActive("live", false));
        assertFalse(registry.isOn("live"));
        assertTrue(registry.isLoaded("live"));
        boolean[] ran = {false};
        assertFalse(registry.guard("live", () -> ran[0] = true));
        assertFalse(ran[0], "止めている間は中身を走らせない");
        assertEquals(bootLine, registry.line(), "止めても起動の 1 行は変えない");
        assertEquals(1, lines.size(), "止めても行を出し直さない");

        assertTrue(registry.setActive("live", true));
        assertTrue(registry.isOn("live"));
        assertEquals(List.of(false, true), hook);

        assertFalse(registry.setActive("cut", true), "起動のときに切ってあった機能は起動し直すまで動かない");
        assertFalse(registry.isOn("cut"));
        assertTrue(registry.enableableByRestart("cut"));
        assertFalse(registry.enableableByRestart("cut_missing"), "要る Mod が無ければ、起動し直しても有効にならない");

        registry.markFailed("broken", "boom");
        assertFalse(registry.setActive("broken", true), "動かし直しても FAILED は消えない");
        assertFalse(registry.isOn("broken"));
        assertFalse(registry.enableableByRestart("broken"));
    }
}
