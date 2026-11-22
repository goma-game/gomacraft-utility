package gomacraft.utility.emiae;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gomacraft.utility.feature.FeatureConfig;
import gomacraft.utility.feature.FeatureRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmiAeFeaturesTest {
    /** 画面だけの機能(EMI と AE2 が要る)。 */
    private static final String CLIENT_IDS = "emi_ae_stock emi_ae_craft_confirm emi_ae_quick_craft";

    private static String line(Map<String, String> mods, boolean dedicatedServer) {
        return FeatureRegistry.evaluate(EmiAeFeatures.all(), id -> Optional.ofNullable(mods.get(id)),
                FeatureConfig.parse(""), s -> { }, dedicatedServer).line();
    }

    /** 画面だけの機能が同じ状態で、問い合わせの口が {@code query} のときの 1 行。 */
    private static String expect(String clientState, String query) {
        StringBuilder sb = new StringBuilder("gomacraft_utility: features");
        for (String id : CLIENT_IDS.split(" ")) {
            sb.append(' ').append(id).append('=').append(clientState);
        }
        return sb.append(" ae_stock_query=").append(query).toString();
    }

    @Test
    void onForTheVersionsTheServerRuns() {
        // 版の文字列は FML が出す形(EMI は「+<Minecraft の版>」が付く)
        assertEquals(expect("on", "on"), line(Map.of("ae2", "19.2.18", "emi", "1.1.24+1.21.1"), false));
        assertEquals(expect("on", "on"), line(Map.of("ae2", "19.2.19", "emi", "1.1.24+1.21.1"), false));
    }

    @Test
    void screenFeaturesAreOffOnTheDedicatedServerButTheQueryAnswers() {
        assertEquals(expect("off(client only)", "on"), line(Map.of("ae2", "19.2.18", "emi", "1.1.24+1.21.1"), true));
        // サーバの答えは EMI が無くても動く
        assertEquals(expect("off(mod missing: emi)", "on"), line(Map.of("ae2", "19.2.18"), true));
    }

    @Test
    void offForOtherSeriesAndMissingMods() {
        assertEquals(expect("off(version: emi 1.2.0+1.21.1 outside [1.1,1.2})", "on"),
                line(Map.of("ae2", "19.2.18", "emi", "1.2.0+1.21.1"), false));
        assertEquals(expect("off(mod missing: ae2)", "off(mod missing: ae2)"), line(Map.of("emi", "1.1.24+1.21.1"), false));
        assertEquals(expect("off(version: ae2 20.0.0 outside [19.2,19.3})", "off(version: ae2 20.0.0 outside [19.2,19.3})"),
                line(Map.of("ae2", "20.0.0", "emi", "1.1.24+1.21.1"), false));
    }
}
