package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import androidx.compose.foundation.layout.Column
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
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.impl.domain.models.Market

@Composable
fun SelectMarketContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
    ) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(Alignment.CenterHorizontally))
        MarginVertical(margin = 8.dp)

        Text(
            text = stringResource(R.string.polkaswap_market_algorithm_title),
            style = MaterialTheme.customTypography.header3
        )

        Market.values().forEach {
            ExapandableText(
                modifier = Modifier
                    .padding(vertical = 16.dp),
                title = it.marketName,
                initialState = false
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
    SelectMarketContent()
}
