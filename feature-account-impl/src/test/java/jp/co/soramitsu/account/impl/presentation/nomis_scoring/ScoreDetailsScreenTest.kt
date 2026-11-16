package jp.co.soramitsu.account.impl.presentation.nomis_scoring

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class ScoreDetailsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : ScoreDetailsScreenCallback {
        override fun onCloseClicked() { calls += "close" }
        override fun onCopyAddressClicked() { calls += "copy" }
    }

    private val info = ScoreInfoState(
        score = 72,
        updated = "Nov 16, 2026",
        nativeBalanceUsd = "$12.00",
        holdTokensUsd = "$340.00",
        walletAge = "3 years",
        totalTransactions = "1204",
        rejectedTransactions = "7",
        avgTransactionTime = "5 hours",
        maxTransactionTime = "40 hours",
        minTransactionTime = "1 hours"
    )

    private fun render(state: ScoreDetailsScreenState) {
        compose.setContent { FearlessAppTheme { ScoreDetailsContent(state = state, callback = callback) } }
    }

    @Test
    fun successShowsTheScoreAndEveryStatistic() {
        render(ScoreDetailsScreenState(address = "0x1234567890abcdef1234567890abcdef12345678", info = ScoreDetailsViewState.Success(info)))

        compose.onNodeWithText(string(R.string.account_stats_title)).assertIsDisplayed()
        compose.onNodeWithText("72").assertIsDisplayed()
        listOf("Nov 16, 2026", "$12.00", "$340.00", "3 years", "1204", "7", "5 hours", "40 hours", "1 hours")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText(string(R.string.account_stats_min_transaction_time_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.account_stats_error_message)).assertDoesNotExist()
    }

    @Test
    fun addressCopiesAndCloseButtonCloses() {
        render(ScoreDetailsScreenState(address = "0x1234567890abcdef1234567890abcdef12345678", info = ScoreDetailsViewState.Success(info)))

        compose.onNodeWithTag("address", useUnmergedTree = true).tap()
        compose.onNodeWithText(string(R.string.common_close)).tap()
        compose.waitForIdle()

        assertEquals(listOf("copy", "close"), calls)
    }

    @Test
    fun loadingShowsTheTitlesWithoutAScoreOrAddress() {
        render(ScoreDetailsScreenState(address = null, info = ScoreDetailsViewState.Loading))

        compose.onNodeWithText(string(R.string.account_stats_updated_title)).assertIsDisplayed()
        compose.onNodeWithText("72").assertDoesNotExist()
        compose.onNodeWithTag("address").assertDoesNotExist()
        compose.onNodeWithText(string(R.string.account_stats_error_message)).assertDoesNotExist()
    }

    @Test
    fun errorShowsTheErrorMessageInsteadOfTheTable() {
        render(ScoreDetailsScreenState(address = "0xabc", info = ScoreDetailsViewState.Error))

        compose.onNodeWithText(string(R.string.account_stats_error_message)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.account_stats_updated_title)).assertDoesNotExist()
    }

    @Test
    fun scoreBarAlwaysDrawsFiveStarsForKnownStates() {
        // Five 30 dp stars with 8 dp gaps, whatever mix of full, half and empty stars the score gives.
        val scores = listOf(0, 4, 5, 33, 50, 66, 99, 100, NomisScoreData.LOADING_CODE, NomisScoreData.ERROR_CODE)
        val score = mutableIntStateOf(scores.first())
        compose.setContent {
            FearlessAppTheme { Box(Modifier.testTag("bar")) { ScoreBar(score = score.intValue) } }
        }
        scores.forEach {
            score.intValue = it
            compose.waitForIdle()
            compose.onNodeWithTag("bar").assertWidthIsEqualTo(182.dp)
        }
    }

    @Test
    fun scoreBarIsEmptyForAnUnknownCode() {
        compose.setContent { FearlessAppTheme { Box(Modifier.testTag("bar")) { ScoreBar(score = -3) } } }

        compose.onNodeWithTag("bar").assertWidthIsEqualTo(0.dp)
    }

    @Test
    fun previewShowsTheSampleScore() {
        compose.setContent {
            PrivateComposable("jp.co.soramitsu.account.impl.presentation.nomis_scoring.ScoreDetailsScreenKt", "ScoreDetailsScreenPreview")
        }

        compose.onNodeWithText("55").assertIsDisplayed()
        compose.onNodeWithText("1 year").assertIsDisplayed()
    }
}
