package jp.co.soramitsu.account.impl.presentation.appearance

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.FwTierChoice
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class AppearanceScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun render(state: AppearanceState, tier: FwTier = FwTier.Full, fontScale: Float = 1f) {
        compose.setContent {
            FearlessAppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalFwEffects provides FwEffects(tier, highContrast = false, reduceMotion = false),
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    AppearanceScreen(
                        state = state,
                        scrollState = rememberScrollState(),
                        onBack = { calls += "back" },
                        onReducedChanged = { calls += "reduced:$it" },
                        onContrastChanged = { calls += "contrast:$it" },
                        onRetry = { calls += "retry" }
                    )
                }
            }
        }
    }

    @Test
    fun automaticEffectsOfferReducedAndHighContrast() {
        render(AppearanceState(FwAppearance(FwTierChoice.Automatic, highContrast = false)))

        compose.onNodeWithText(string(R.string.appearance_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.appearance_effects_automatic_description)).assertIsDisplayed()
        compose.onNodeWithTag("appearance_contrast").assertIsOff()
        compose.onNodeWithTag("appearance_kept_reduced").assertDoesNotExist()
        compose.onNodeWithTag("appearance_save_failed").assertDoesNotExist()
        compose.onNodeWithTag("appearance_preview").assertExists()

        compose.onNodeWithTag("appearance_effects_reduced").tap()
        compose.onNodeWithTag("appearance_contrast").tap()
        compose.waitForIdle()

        assertEquals(listOf("reduced:true", "contrast:true"), calls)
    }

    @Test
    fun reducedChoiceDescribesReducedEffectsAndCanReturnToAutomatic() {
        render(AppearanceState(FwAppearance(FwTierChoice.Reduced, highContrast = true)), fontScale = 1.5f)

        compose.onNodeWithText(string(R.string.appearance_effects_reduced_description)).assertIsDisplayed()
        compose.onNodeWithTag("appearance_contrast").assertIsOn()

        compose.onNodeWithTag("appearance_effects_automatic").tap()
        compose.onNodeWithTag("appearance_contrast").tap()
        compose.waitForIdle()

        assertEquals(listOf("reduced:false", "contrast:false"), calls)
    }

    @Test
    fun deviceThatPaintsReducedExplainsWhyAutomaticStaysReduced() {
        render(AppearanceState(FwAppearance(FwTierChoice.Automatic, highContrast = false)), tier = FwTier.Reduced)

        compose.onNodeWithTag("appearance_kept_reduced").assertIsDisplayed()
    }

    @Test
    fun failedSaveOffersRetryAndBackLeavesThePage() {
        render(AppearanceState(FwAppearance(), saveFailed = true))

        compose.onNodeWithTag("appearance_save_failed").assertIsDisplayed()
        compose.onNodeWithText(string(jp.co.soramitsu.common.R.string.common_retry)).tap()
        compose.waitForIdle()

        assertEquals(listOf("retry"), calls)
    }
}
