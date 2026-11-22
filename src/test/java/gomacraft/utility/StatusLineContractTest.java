package gomacraft.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gomacraft.utility.feature.FeatureState;
import gomacraft.utility.feature.FeatureStatus;
import gomacraft.utility.feature.StatusLine;
import gomacraft.utility.mixinsupport.GomacraftMixinPlugin;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 起動時の 1 行の形(gomabase の起動の検証との約束)。見本の文字列は gomabase の試験にも同じものを置く。
 */
class StatusLineContractTest {
    /** gomabase の読み手と同じ文法。 */
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9_]+=(on|off|FAILED)(\\([^()]*\\))?");

    @Test
    void sampleLinesMatchTheContract() {
        assertEquals("gomacraft_utility: features none", StatusLine.render(List.of()));
        assertEquals(
                "gomacraft_utility: features ae2_search=on compass=off(mod missing: compass)"
                        + " emi_link=FAILED(InvalidInjectionException: no target)",
                StatusLine.render(List.of(
                        new FeatureStatus("ae2_search", FeatureState.ON, null),
                        new FeatureStatus("compass", FeatureState.OFF, "mod missing: compass"),
                        new FeatureStatus("emi_link", FeatureState.FAILED, "InvalidInjectionException: no target"))));
    }

    @Test
    void everyTokenParses() {
        String line = StatusLine.render(List.of(
                new FeatureStatus("a", FeatureState.OFF, "version: x 1.0 outside [1.0,2.0)"),
                new FeatureStatus("b", FeatureState.FAILED, "multi\nline (nested (parens))")));
        String body = line.substring(StatusLine.PREFIX.length());
        // 理由は空白を含みうるので、語の区切りは文法で読む(gomabase の読み手と同じ)
        assertTrue(Pattern.compile("(" + TOKEN.pattern() + ")( " + TOKEN.pattern() + ")*").matcher(body).matches(),
                body);
    }

    @Test
    void catalogIdsAreValidAndUnique() {
        Set<String> seen = new HashSet<>();
        for (boolean selftest : new boolean[] {false, true}) {
            for (boolean client : new boolean[] {false, true}) {
                seen.clear();
                Features.catalog(selftest, client).forEach(f -> {
                    assertTrue(f.id().matches("[a-z0-9_]+"), f.id());
                    assertTrue(seen.add(f.id()), f.id());
                });
            }
        }
        for (boolean client : new boolean[] {false, true}) {
            assertTrue(Features.catalog(false, client).stream().noneMatch(f -> f.id().startsWith("selftest")),
                    "自己検査の機能は配る jar の一覧に入れない");
        }
    }

    @Test
    void mixinPackageMapsToFeature() {
        assertEquals("ae2_search", GomacraftMixinPlugin.featureOf("gomacraft.utility.mixin.ae2_search.TerminalMixin"));
        assertNull(GomacraftMixinPlugin.featureOf("gomacraft.utility.mixin.Loose"));
        assertNull(GomacraftMixinPlugin.featureOf("other.mod.mixin.ae2_search.X"));
    }
}
