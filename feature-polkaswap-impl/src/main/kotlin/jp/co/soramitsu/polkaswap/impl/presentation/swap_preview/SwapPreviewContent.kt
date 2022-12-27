package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.feature_polkaswap_impl.R

interface SwapPreviewCallbacks {

    fun onBackClick()

    fun onConfirmClick()
}

@Composable
fun SwapPreviewContent(
    callbacks: SwapPreviewCallbacks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row {
            NavigationIconButton(
                modifier = Modifier.padding(start = 16.dp),
                onNavigationClick = callbacks::onBackClick
            )

            Text(
                text = stringResource(R.string.polkaswap_preview_title),
                style = MaterialTheme.customTypography.header4
            )
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            GradientIcon(
                iconRes = R.drawable.ic_fearless_logo,
                color = colorAccentDark,
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Text(
                text = stringResource(R.string.polkaswap_swap_title),
                style = MaterialTheme.customTypography.header2
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "1,000,000 XOR",
                    style = MaterialTheme.customTypography.header3
                )

                MarginHorizontal(margin = 16.dp)

                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right_24),
                    contentDescription = null
                )

                MarginHorizontal(margin = 16.dp)

                Text(
                    text = "1,000,000 XOR",
                    style = MaterialTheme.customTypography.header3
                )
            }

            InfoTable(
                items = listOf(
                    TitleValueViewState(
                        title = "Min Received",
                        value = "1.739664 ETH",
                        additionalValue = "~\$3,343.70"
                    ),
                    TitleValueViewState(
                        title = "Route",
                        value = "XOR -> ETH"
                    ),
                    TitleValueViewState(
                        title = "XOR / ETH",
                        value = "0.00763842"
                    ),
                    TitleValueViewState(
                        title = "ETH / XOR",
                        value = "130.14852941"
                    ),
                    TitleValueViewState(
                        title = "Price Impact",
                        value = "-0.01%"
                    ),
                    TitleValueViewState(
                        title = "Network Fee",
                        value = "0.0007 XOR",
                        additionalValue = "~\$ 0.32"
                    )
                )
            )
        }

        AccentButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.common_confirm),
            onClick = callbacks::onConfirmClick
        )
    }
}

@Preview
@Composable
fun SwapPreviewContentPreview() {
    SwapPreviewContent(
        callbacks = object : SwapPreviewCallbacks {
            override fun onBackClick() {
            }

            override fun onConfirmClick() {
            }
        }
    )
}
