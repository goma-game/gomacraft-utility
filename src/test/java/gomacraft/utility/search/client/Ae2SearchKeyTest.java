package gomacraft.utility.search.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.neoforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

/** {@link Ae2SearchKey} の修飾キー判定が Ctrl+F 等を横取りしないことを検証する。 */
class Ae2SearchKeyTest {
    @Test
    void noneModifierAllowsUnmodifiedAndAltOnly() {
        assertTrue(Ae2SearchKey.matchesModifier(KeyModifier.NONE, 0, false));
        assertTrue(Ae2SearchKey.matchesModifier(KeyModifier.NONE, GLFW.GLFW_MOD_ALT, false));
        assertFalse(Ae2SearchKey.matchesModifier(KeyModifier.NONE, GLFW.GLFW_MOD_CONTROL, false));
        assertFalse(Ae2SearchKey.matchesModifier(KeyModifier.NONE, GLFW.GLFW_MOD_SHIFT, false));
        assertFalse(Ae2SearchKey.matchesModifier(KeyModifier.NONE, GLFW.GLFW_MOD_SUPER, false));
        assertFalse(Ae2SearchKey.matchesModifier(
                KeyModifier.NONE, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT, false));
    }

    @Test
    void explicitModifierDelegatesToActiveFlag() {
        assertTrue(Ae2SearchKey.matchesModifier(KeyModifier.CONTROL, GLFW.GLFW_MOD_CONTROL, true));
        assertFalse(Ae2SearchKey.matchesModifier(KeyModifier.CONTROL, 0, false));
    }
}
