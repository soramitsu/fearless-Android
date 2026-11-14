package jp.co.soramitsu.account.impl.presentation.appearance

import jp.co.soramitsu.common.compose.theme.fw.FwAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwTierChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceViewModelTest {
    @Test fun storedLiteReadsAsTheAutomaticItPaintsLike() {
        assertEquals(
            FwAppearance(FwTierChoice.Automatic, highContrast = true),
            AppearanceViewModel.displayed(FwAppearance(FwTierChoice.Lite, highContrast = true))
        )
    }

    @Test fun offeredChoicesReadBackUnchanged() {
        for (stored in listOf(FwAppearance(), FwAppearance(FwTierChoice.Reduced), FwAppearance(highContrast = true))) {
            assertEquals(stored, AppearanceViewModel.displayed(stored))
        }
    }
}
