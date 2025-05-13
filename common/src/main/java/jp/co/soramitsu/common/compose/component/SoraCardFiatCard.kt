package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.IbanStatus

data class SoraCardItemViewState(
    val kycStatus: String? = null,
    val visible: Boolean,
    val loading: Boolean,
    val success: Boolean,
    val iban: IbanInfo?,
    val buyXor: SoraCardBuyXorState? = null,
    val soraCardProgress: SoraCardProgress,
)

val previewSoraCardItemViewState = SoraCardItemViewState(
    kycStatus = "",
    visible = true,
    success = true,
    iban = IbanInfo(
        iban = "EDF-98-NBV-334E",
        ibanStatus = IbanStatus.ACTIVE,
        balance = "3 456",
        statusDescription = "desc",
    ),
    buyXor = SoraCardBuyXorState(true),
    soraCardProgress = SoraCardProgress.START,
    loading = false,
)

data class SoraCardBuyXorState(
    val enabled: Boolean,
)

enum class SoraCardProgress {
    START, KYC_IBAN,
}

@Composable
fun SoraCardFiatCard(
    state: SoraCardItemViewState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BackgroundCornered(
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .height(80.dp)
                .padding(end = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sora_card_fiat_right),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.width(54.dp)
            )
            MarginHorizontal(margin = 4.dp)
            Divider(
                color = white16,
                modifier = Modifier
                    .height(64.dp)
                    .width(1.dp)
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(jp.co.soramitsu.oauth.R.string.common_fiat),
                    style = MaterialTheme.customTypography.capsTitle2,
                    modifier = Modifier
                        .alpha(0.64f)
                )
                Row(
                    verticalAlignment = CenterVertically,
                ) {
                    Text(
                        text = stringResource(jp.co.soramitsu.oauth.R.string.common_sora_card),
                        style = MaterialTheme.customTypography.header3,
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(vertical = 4.dp)
                    )
                    if (state.iban != null && state.iban.ibanStatus != IbanStatus.CLOSED) {
                        Text(
                            text = state.iban.balance,
                            style = MaterialTheme.customTypography.header3.copy(textAlign = TextAlign.End),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .padding(start = 4.dp)
                        )
                    }
                }
                val desc = if (state.iban != null && state.iban.ibanStatus == IbanStatus.CLOSED) {
                    state.iban.statusDescription
                } else if (state.iban == null && state.success) {
                    "--"
                } else if (state.iban != null && state.iban.ibanStatus != IbanStatus.CLOSED && state.iban.iban.isNotEmpty()) {
                    state.iban.iban
                } else if (state.iban == null && state.kycStatus != null) {
                    state.kycStatus
                } else {
                    ""
                }
                Text(
                    text = desc,
                    style = MaterialTheme.customTypography.body1,
                    modifier = Modifier
                        .alpha(0.64f)
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAssetListItem() {
    FearlessAppTheme {
        Box(modifier = Modifier.background(MaterialTheme.customColors.black)) {
            Column {
                SoraCardFiatCard(
                    state = previewSoraCardItemViewState,
                    modifier = Modifier,
                    onClick = {},
                )
                MarginVertical(margin = 8.dp)
                SoraCardFiatCard(
                    state = SoraCardItemViewState(
                        kycStatus = "kyc in progress",
                        visible = true,
                        success = false,
                        iban = null,
                        buyXor = SoraCardBuyXorState(true),
                        soraCardProgress = SoraCardProgress.KYC_IBAN,
                        loading = false,
                    ),
                    modifier = Modifier,
                    onClick = {},
                )
                MarginVertical(margin = 8.dp)
                SoraCardFiatCard(
                    state = SoraCardItemViewState(
                        kycStatus = "doesn't matter",
                        visible = true,
                        success = true,
                        iban = IbanInfo(
                            iban = "IBG-567-NB",
                            ibanStatus = IbanStatus.CLOSED,
                            balance = "123.9",
                            statusDescription = "closed",
                        ),
                        buyXor = SoraCardBuyXorState(true),
                        soraCardProgress = SoraCardProgress.KYC_IBAN,
                        loading = false,
                    ),
                    modifier = Modifier,
                    onClick = {},
                )
                MarginVertical(margin = 8.dp)
                SoraCardFiatCard(
                    state = SoraCardItemViewState(
                        kycStatus = "doesn't matter",
                        visible = true,
                        success = true,
                        iban = null,
                        buyXor = SoraCardBuyXorState(true),
                        soraCardProgress = SoraCardProgress.KYC_IBAN,
                        loading = false,
                    ),
                    modifier = Modifier,
                    onClick = {},
                )
            }
        }
    }
}
