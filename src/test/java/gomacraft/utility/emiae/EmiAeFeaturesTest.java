package gomacraft.utility.emiae;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmiAeFeaturesTest {
    private static final String IDS = "emi_ae_stock emi_ae_craft_confirm emi_ae_quick_craft";

    private static String line(Map<String, String> mods, boolean dedicatedServer) {
        return FeatureRegistry.evaluate(EmiAeFeatures.all(), id -> Optional.ofNullable(mods.get(id)),
                FeatureConfig.parse(""), s -> { }, dedicatedServer).line();
    }

    /** 全部の機能が同じ状態のときの 1 行。 */
    private static String all(String state) {
        StringBuilder sb = new StringBuilder("gomacraft_utility: features");
        for (String id : IDS.split(" ")) {
            sb.append(' ').append(id).append('=').append(state);
        }
        return sb.toString();
    }

    @Test
    void onForTheVersionsTheServerRuns() {
        // 版の文字列は FML が出す形(EMI は「+<Minecraft の版>」が付く)
        assertEquals(all("on"), line(Map.of("ae2", "19.2.18", "emi", "1.1.24+1.21.1"), false));
        assertEquals(all("on"), line(Map.of("ae2", "19.2.19", "emi", "1.1.24+1.21.1"), false));
    }

    @Test
    void offOnTheDedicatedServerAndForOtherSeries() {
        assertEquals(all("off(client only)"), line(Map.of("ae2", "19.2.18", "emi", "1.1.24+1.21.1"), true));
        assertEquals(all("off(version: emi 1.2.0+1.21.1 outside [1.1,1.2})"),
                line(Map.of("ae2", "19.2.18", "emi", "1.2.0+1.21.1"), false));
        assertEquals(all("off(mod missing: ae2)"), line(Map.of("emi", "1.1.24+1.21.1"), false));
    }
}
