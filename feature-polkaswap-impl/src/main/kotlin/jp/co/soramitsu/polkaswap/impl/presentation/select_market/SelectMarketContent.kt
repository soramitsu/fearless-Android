package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ExapandableText
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market

@Composable
fun SelectMarketContent(
    state: LoadingState<List<Market>>,
    modifier: Modifier = Modifier,
    marketSelected: (Market) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(Alignment.CenterHorizontally))
        MarginVertical(margin = 8.dp)

        Text(
            text = stringResource(R.string.polkaswap_market_algorithm_title),
            style = MaterialTheme.customTypography.header3
        )

        MarginVertical(margin = 16.dp)
        val markets = state as? LoadingState.Loaded
        markets?.data?.forEach {
            ExapandableText(
                modifier = Modifier
                    .padding(vertical = 16.dp),
                title = it.marketName,
                initialState = false,
                isFullWidthClickable = false,
                onClick = { marketSelected(it) }
            ) {
                Text(
                    text = stringResource(it.descriptionId),
                    style = MaterialTheme.customTypography.body1,
                    color = MaterialTheme.customColors.white50
                )
            }
        }
    }
}

@Preview
@Composable
fun SelectMarketContentPreview() {
    FearlessTheme {
        SelectMarketContent(LoadingState.Loading()) {}
    }
}
