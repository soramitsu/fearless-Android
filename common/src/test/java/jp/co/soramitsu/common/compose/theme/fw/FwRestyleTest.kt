package jp.co.soramitsu.common.compose.theme.fw

import jp.co.soramitsu.common.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FwRestyleTest {
    @Test fun legacyRollbackWinsEvenWhenAllSurfaceFlagsAreRequested() {
        assertFalse(FwRestyle.resolve("legacy", true))
        assertFalse(FwRestyle.resolve("legacy", false))
    }

    @Test fun individualRollbackDoesNotRequireChangingThePalette() {
        assertTrue(FwRestyle.resolve("v1", true))
        assertFalse(FwRestyle.resolve("v1", false))
    }

    @Test fun unexpectedPaletteCannotAccidentallyEnableTheRestyle() {
        for (palette in listOf("", "V1", " v1", "v1 ", "future")) {
            assertFalse(FwRestyle.resolve(palette, true))
        }
    }

    @Test fun compiledFlagsResolveEverySurface() {
        // The build qualification runs this against independently inspected generated flag matrices.
        assertEquals(14, FwSurface.entries.size)
        for (surface in FwSurface.entries) {
            assertEquals(BuildConfig.FW_PALETTE == "v1" && surface.requested, FwRestyle.isEnabled(surface))
        }
    }
}
