package jp.co.soramitsu.soracard.impl.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.IbanStatus
import jp.co.soramitsu.oauth.uiscreens.clientsui.SoraCardImage
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

data class SoraCardItemViewState(
    val kycStatus: String? = null,
    val visible: Boolean,
    val success: Boolean,
    val iban: IbanInfo?,
    val buyXor: SoraCardBuyXorState? = null ,
)

data class SoraCardBuyXorState(
    val enabled: Boolean,
)

@Composable
fun SoraCardItem(
    state: SoraCardItemViewState,
    onClose: (() -> Unit),
    onClick: (() -> Unit)
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        SoraCardImage()
        CardStateButton(
            modifier = Modifier
                .wrapContentWidth()
                .run {
                    if (state.success.not() && state.iban == null)
                        padding(bottom = Dimens.x3) else padding(all = Dimens.x1)
                }
                .run {
                    if (state.success.not() && state.iban == null)
                        align(Alignment.BottomCenter) else align(Alignment.BottomEnd)
                },
            kycStatus = state.kycStatus,
            ibanInfo = state.iban,
            success = state.success,
            onCardStateClicked = onClick,
        )

        if (state.success.not() && state.iban == null)
            BleachedButton(
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.TopEnd)
                    .padding(Dimens.x1),
                size = Size.ExtraSmall,
                order = Order.TERTIARY,
                shape = CircleShape,
                onClick = onClose,
                leftIcon = painterResource(jp.co.soramitsu.ui_core.R.drawable.ic_cross),
            )

    }
}

@Composable
private fun CardStateButton(
    modifier: Modifier = Modifier,
    kycStatus: String?,
    ibanInfo: IbanInfo?,
    success: Boolean,
    onCardStateClicked: () -> Unit
) {
    if (ibanInfo != null) {
        BleachedButton(
            modifier = modifier,
            size = Size.ExtraSmall,
            order = Order.SECONDARY,
            onClick = onCardStateClicked,
            text = if (ibanInfo.ibanStatus != IbanStatus.CLOSED) ibanInfo.balance else ibanInfo.statusDescription,
        )
    } else if (kycStatus == null) {
        FilledButton(
            modifier = modifier,
            size = Size.Large,
            order = Order.SECONDARY,
            onClick = onCardStateClicked,
            text = stringResource(R.string.sora_card_get_sora_card),
        )
    } else {
        TonalButton(
            modifier = modifier,
            size = Size.Large,
            order = Order.TERTIARY,
            onClick = onCardStateClicked,
            text = if (success) "--" else kycStatus,
        )
    }
}

@Preview
@Composable
private fun SoraCardItemItemPreview() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        val state = SoraCardItemViewState(
            null,
            visible = false,
            success = false,
            iban = null,
        )
        SoraCardItem(state = state, {}, {})
        MarginVertical(margin = 8.dp)
        val state2 = SoraCardItemViewState(
            "Kyc Status",
            visible = false,
            success = false,
            iban = null,
        )
        SoraCardItem(state = state2, {}, {})
    }
}

@Preview
@Composable
private fun SoraCardItemItemPreview2() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SoraCardItem(state = SoraCardItemViewState(
            "Kyc Status",
            visible = false,
            success = true,
            iban = null,
        ), {}, {})
        MarginVertical(margin = 8.dp)
        SoraCardItem(state = SoraCardItemViewState(
            "Kyc Status",
            visible = false,
            success = true,
            iban = IbanInfo(
                iban = "iban",
                ibanStatus = IbanStatus.ACTIVE,
                balance = "123.3",
                statusDescription = "iban desc",
            ),
        ), {}, {})
    }
}
