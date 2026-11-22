package gomacraft.utility.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureConfigUpdateTest {
    @Test
    void replacesValuesInPlaceKeepingCommentsAndAddsMissingOnes() {
        String before = """
                # 頭の説明
                [features]
                emi_ae_stock = true  # 在庫数
                emi_ae_quick_craft = false

                [other]
                emi_ae_stock = true
                """;
        Map<String, Boolean> changes = new LinkedHashMap<>();
        changes.put("emi_ae_stock", false);
        changes.put("screenshot_share", true);
        String after = FeatureConfig.update(before, changes);
        assertEquals("""
                # 頭の説明
                [features]
                emi_ae_stock = false  # 在庫数
                emi_ae_quick_craft = false
                screenshot_share = true

                [other]
                emi_ae_stock = true
                """, after);
        // 読み直すと、変えた値と、触っていない値のまま
        FeatureConfig read = FeatureConfig.parse(after);
        assertEquals(FeatureConfig.Value.FALSE, read.value("emi_ae_stock").orElseThrow());
        assertEquals(FeatureConfig.Value.TRUE, read.value("screenshot_share").orElseThrow());
        assertEquals(FeatureConfig.Value.FALSE, read.value("emi_ae_quick_craft").orElseThrow());
    }

    @Test
    void replacesEveryDuplicateAndDoesNotRepeatAnEmptyHeader() {
        String after = FeatureConfig.update("[features]\na = true\nb = true\na = true\n", Map.of("a", false));
        assertEquals("[features]\na = false\nb = true\na = false\n", after);
        String empty = FeatureConfig.update("[features]\n", Map.of("a", false));
        assertEquals("[features]\na = false\n", empty);
    }

    @Test
    void addsTheSectionWhenMissing() {
        String after = FeatureConfig.update("# 空\n", Map.of("emi_ae_stock", false));
        assertEquals(FeatureConfig.Value.FALSE, FeatureConfig.parse(after).value("emi_ae_stock").orElseThrow());
    }
}
