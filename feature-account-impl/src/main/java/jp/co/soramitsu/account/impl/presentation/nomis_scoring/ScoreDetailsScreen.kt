package jp.co.soramitsu.account.impl.presentation.nomis_scoring

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.Address
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.EmptyMessage
import jp.co.soramitsu.common.compose.component.FearlessCorneredShape
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.warningYellow
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.compose.theme.white30
import jp.co.soramitsu.common.data.network.runtime.binding.cast

data class ScoreDetailsScreenState(
    val address: String?,
    val info: ScoreDetailsViewState
)

sealed class ScoreDetailsViewState {
    data object Loading : ScoreDetailsViewState()
    data class Success(val data: ScoreInfoState) : ScoreDetailsViewState()
    data object Error : ScoreDetailsViewState()
}

data class ScoreInfoState(
    val score: Int,
    val updated: String,
    val nativeBalanceUsd: String,
    val holdTokensUsd: String,
    val walletAge: String,
    val totalTransactions: String,
    val rejectedTransactions: String,
    val avgTransactionTime: String,
    val maxTransactionTime: String,
    val minTransactionTime: String
)

interface ScoreDetailsScreenCallback {
    fun onBackClicked()
    fun onCloseClicked()
    fun onCopyAddressClicked()
}

@Composable
fun ScoreDetailsContent(
    state: ScoreDetailsScreenState,
    callback: ScoreDetailsScreenCallback
) {
    val verticalScrollModifier = if(state.info is ScoreDetailsViewState.Success) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(modifier = verticalScrollModifier
        .fillMaxSize()
        ) {
        Toolbar(
            state = ToolbarViewState(
                stringResource(id = R.string.account_stats_title),
                R.drawable.ic_arrow_back_24dp,
                listOf(
                    MenuIconItem(
                        icon = R.drawable.ic_cross_24,
                        onClick = callback::onCloseClicked
                    )
                )
            ),
            onNavigationClick = callback::onBackClicked
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MarginVertical(margin = 24.dp)
            when (state.info) {
                is ScoreDetailsViewState.Error -> ScoreBar(-2)
                ScoreDetailsViewState.Loading -> ScoreBar(-1)
                is ScoreDetailsViewState.Success -> ScoreBar(state.info.data.score)
            }

            MarginVertical(margin = 16.dp)
            B2(
                text = stringResource(id = R.string.account_stats_description_text),
                textAlign = TextAlign.Center,
                color = black2
            )
            MarginVertical(margin = 6.dp)
            state.info.takeIf { it is ScoreDetailsViewState.Success }
                ?.cast<ScoreDetailsViewState.Success>()?.let {
                    H1(text = it.data.score.toString())
                }
            MarginVertical(margin = 8.dp)
            state.address?.let { Address(address = it, onClick = callback::onCopyAddressClicked) }
            MarginVertical(margin = 16.dp)

            when (val info = state.info) {
                is ScoreDetailsViewState.Error -> Error()
                ScoreDetailsViewState.Loading -> ScoresInfoTable(null)
                is ScoreDetailsViewState.Success -> ScoresInfoTable(state = info.data)
            }
            MarginVertical(margin = 16.dp)
            AccentButton(
                state = ButtonViewState(text = stringResource(id = R.string.common_close)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = callback::onCloseClicked
            )
            MarginVertical(margin = 16.dp)
        }
    }
}

@Composable
private fun Error() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, color = white24, shape = FearlessCorneredShape()),
        shape = FearlessCorneredShape(),
        color = black05
    ) {
        Box(modifier = Modifier.padding(vertical = 64.dp)) {

            EmptyMessage(
                message = R.string.account_stats_error_message,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

}

@Composable
private fun ScoresInfoTable(state: ScoreInfoState?) {
    InfoTable(
        items = listOf(
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_updated_title),
                value = state?.updated
            ),
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_native_balance_usd_title),
                value = state?.nativeBalanceUsd
            ),
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_hold_tokens_usd_title),
                value = state?.holdTokensUsd
            ),
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_wallet_age_title),
                value = state?.walletAge
            ),
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_total_transactions_title),
                value = state?.totalTransactions
            ),
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_rejected_transactions_title),
                value = state?.rejectedTransactions
            ),
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_avg_transaction_time_title),
                value = state?.avgTransactionTime
            ),
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_max_transaction_time_title),
                value = state?.maxTransactionTime
            ),
            TitleValueViewState(
                title = stringResource(id = R.string.account_stats_min_transaction_time_title),
                value = state?.minTransactionTime
            )
        )
    )
}

@Composable
fun ScoreBar(score: Int, modifier: Modifier = Modifier) {
    val color = when (score) {
        in 0..33 -> warningOrange
        in 33..66 -> warningYellow
        in 66..100 -> greenText
        else -> white30
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            score >= 0 -> {
                val fullStars = score / 20
                val halfStar = if (score % 20 >= 5) 1 else 0
                val emptyStars = 5 - fullStars - halfStar

                repeat(fullStars) {
                    Image(
                        res = R.drawable.ic_score_star_full,
                        tint = color,
                        modifier = Modifier.size(30.dp)
                    )
                }
                repeat(halfStar) {
                    Image(
                        res = R.drawable.ic_score_star_half,
                        tint = color,
                        modifier = Modifier.size(30.dp)
                    )
                }
                repeat(emptyStars) {
                    Image(
                        res = R.drawable.ic_score_star_empty,
                        tint = color,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            score == NomisScoreData.LOADING_CODE -> {
                repeat(5) {
                    Image(
                        res = R.drawable.ic_score_star_empty,
                        tint = color,
                        modifier = Modifier
                            .size(30.dp)
                            .shimmer()
                    )
                }
            }

            score == NomisScoreData.ERROR_CODE -> {
                repeat(5) {
                    Image(
                        res = R.drawable.ic_score_star_empty,
                        tint = color,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
@Preview
private fun ScoreDetailsScreenPreview() {
    FearlessAppTheme {
        val info = ScoreInfoState(
            score = 55,
            updated = "Jan 1, 2024",
            nativeBalanceUsd = "\$1,337.69",
            holdTokensUsd = "\$1,337.69",
            walletAge = "1 year",
            totalTransactions = "1337",
            rejectedTransactions = "40",
            avgTransactionTime = "95 hours",
            maxTransactionTime = "30 hours",
            minTransactionTime = "1 hours"
        )
        val successState = ScoreDetailsViewState.Success(info)
        val state = ScoreDetailsScreenState(
            "Blue Bird 0x23f4g34nign234ij134f0134ifm13i4f134f",
            info = ScoreDetailsViewState.Error
        )
        ScoreDetailsContent(state, object : ScoreDetailsScreenCallback {
            override fun onBackClicked() = Unit
            override fun onCloseClicked() = Unit
            override fun onCopyAddressClicked() = Unit
        })
    }
}