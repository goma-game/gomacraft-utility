package gomacraft.utility.emiae;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmiAeFeaturesTest {
    private static String line(Map<String, String> mods, boolean dedicatedServer) {
        return FeatureRegistry.evaluate(EmiAeFeatures.all(), id -> Optional.ofNullable(mods.get(id)),
                FeatureConfig.parse(""), s -> { }, dedicatedServer).line();
    }

    @Test
    void onForTheVersionsTheServerRuns() {
        // 版の文字列は FML が出す形(EMI は「+<Minecraft の版>」が付く)
        assertEquals("gomacraft_utility: features emi_ae_stock=on emi_ae_craft_confirm=on",
                line(Map.of("ae2", "19.2.18", "emi", "1.1.24+1.21.1"), false));
        assertEquals("gomacraft_utility: features emi_ae_stock=on emi_ae_craft_confirm=on",
                line(Map.of("ae2", "19.2.19", "emi", "1.1.24+1.21.1"), false));
    }

    @Test
    void offOnTheDedicatedServerAndForOtherSeries() {
        assertEquals("gomacraft_utility: features emi_ae_stock=off(client only) emi_ae_craft_confirm=off(client only)",
                line(Map.of("ae2", "19.2.18", "emi", "1.1.24+1.21.1"), true));
        assertEquals("gomacraft_utility: features emi_ae_stock=off(version: emi 1.2.0+1.21.1 outside [1.1,1.2})"
                        + " emi_ae_craft_confirm=off(version: emi 1.2.0+1.21.1 outside [1.1,1.2})",
                line(Map.of("ae2", "19.2.18", "emi", "1.2.0+1.21.1"), false));
        assertEquals("gomacraft_utility: features emi_ae_stock=off(mod missing: ae2)"
                        + " emi_ae_craft_confirm=off(mod missing: ae2)",
                line(Map.of("emi", "1.1.24+1.21.1"), false));
    }
}
