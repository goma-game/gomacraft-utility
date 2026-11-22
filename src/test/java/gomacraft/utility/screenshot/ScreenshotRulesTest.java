package gomacraft.utility.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gomacraft.utility.screenshot.ScreenshotRules.Format;
import gomacraft.utility.screenshot.ScreenshotRules.Result;
import gomacraft.utility.screenshot.ScreenshotRules.Size;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ScreenshotRulesTest {
    static byte[] image(String format, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test
    void readsTheSizeFromTheHeaderOfPngAndJpeg() throws IOException {
        assertEquals(Optional.of(new Size(320, 200)), ScreenshotRules.size(image("png", 320, 200)));
        assertEquals(Optional.of(new Size(64, 48)), ScreenshotRules.size(image("jpeg", 64, 48)));
        assertEquals(Optional.of(Format.PNG), ScreenshotRules.sniff(image("png", 2, 2)));
        assertEquals(Optional.of(Format.JPEG), ScreenshotRules.sniff(image("jpeg", 2, 2)));
    }

    @Test
    void refusesWhatIsNotAnImageOrIsTooLarge() throws IOException {
        assertEquals(Optional.of(Result.BAD_IMAGE), ScreenshotRules.check("GIF89a....".getBytes()));
        assertEquals(Optional.of(Result.TOO_LARGE), ScreenshotRules.check(new byte[0]));
        assertEquals(Optional.of(Result.TOO_LARGE), ScreenshotRules.check(new byte[ScreenshotRules.MAX_BYTES + 1]));
        // PNG の印だけ在ってヘッダが切れている
        byte[] cut = java.util.Arrays.copyOf(image("png", 10, 10), 12);
        assertEquals(Optional.of(Result.BAD_IMAGE), ScreenshotRules.check(cut));
        assertTrue(ScreenshotRules.check(image("png", 10, 10)).isEmpty());
    }

    @Test
    void refusesAHeaderThatClaimsAHugeImage() throws IOException {
        byte[] png = image("png", 10, 10);
        // IHDR の幅を 8193 に書き換える(上限 + 1)
        png[16] = 0;
        png[17] = 0;
        png[18] = 0x20;
        png[19] = 0x01;
        assertEquals(Optional.of(Result.BAD_IMAGE), ScreenshotRules.check(png));
    }

    @Test
    void theStemMatchesTheAgreedShape() {
        String stem = ScreenshotRules.stem(Instant.parse("2026-11-22T01:02:03Z"),
                UUID.fromString("0123abcd-0000-0000-0000-000000000000"), 0x00ff10ab);
        assertEquals("20261122T010203Z-0123abcd-00ff10ab", stem);
        assertTrue(ScreenshotRules.STEM.matcher(stem).matches());
    }

    @Test
    void chunksCoverTheWholeImageAndFitTheVanillaLimit() {
        assertTrue(ScreenshotRules.CHUNK_BYTES < 32767);
        int total = ScreenshotRules.CHUNK_BYTES * 2 + 5;
        assertEquals(3, ScreenshotRules.chunkCount(total));
        assertEquals(5, ScreenshotRules.chunkLength(total, 2));
        assertEquals(ScreenshotRules.MAX_CHUNKS, ScreenshotRules.chunkCount(ScreenshotRules.MAX_BYTES));
    }

    @Test
    void unknownResultNumbersBecomeError() {
        assertEquals(Result.ERROR, Result.of(99));
        assertEquals(Result.ACCEPTED, Result.of(0));
    }
}
