package gomacraft.utility.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FeatureConfigTest {
    private static final Feature A = () -> "alpha";

    @Test
    void readsOnlyTheFeaturesSection() {
        FeatureConfig config = FeatureConfig.parse(String.join("\n",
                "# comment",
                "alpha = false",
                "[other]",
                "beta = false",
                "[ features ]",
                "gamma = true",
                "delta=false   # trailing",
                "epsilon = \"true\"",
                "garbage line"));
        assertEquals(Optional.empty(), config.value("alpha"), "[features] の外は読まない");
        assertEquals(Optional.empty(), config.value("beta"));
        assertEquals(Optional.of(FeatureConfig.Value.TRUE), config.value("gamma"));
        assertEquals(Optional.of(FeatureConfig.Value.FALSE), config.value("delta"));
        assertEquals(Optional.of(FeatureConfig.Value.INVALID), config.value("epsilon"));
    }

    @Test
    void writesDefaultsOnceAndKeepsUserEdits(@TempDir Path dir) throws Exception {
        FeatureConfig first = FeatureConfig.load(dir, List.of(A));
        assertEquals(Optional.of(FeatureConfig.Value.TRUE), first.value("alpha"));

        Path file = dir.resolve(FeatureConfig.FILE_NAME);
        Files.writeString(file, "[features]\nalpha = false\n", StandardCharsets.UTF_8);
        FeatureConfig second = FeatureConfig.load(dir, List.of(A));
        assertEquals(Optional.of(FeatureConfig.Value.FALSE), second.value("alpha"));
        assertTrue(Files.readString(file).contains("alpha = false"), "人が書いた値を消さない");
    }
}
