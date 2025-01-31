package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AddressDisplay
import jp.co.soramitsu.common.compose.component.AddressDisplayState
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.DisabledTextInput
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransferDetailsState

interface TransactionDetailsCallbacks {
    fun onNavigationClick()
    fun onHashClick()
    fun onFromClick()
    fun onToClick()
}

@Composable
fun TransferDetailScreen(state: TransferDetailsState, callback: TransactionDetailsCallbacks) {
    Column {
        MarginVertical(8.dp)
        Toolbar(ToolbarViewState(stringResource(R.string.common_details), R.drawable.ic_cross), onNavigationClick = callback::onNavigationClick)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            MarginVertical(16.dp)
            DisabledTextInput(state.id.hint, state.id.text, endIcon = state.id.endIcon, callback::onHashClick)
            MarginVertical(8.dp)
            state.firstAddress?.let {
                AddressDisplay(it, endIconClick = callback::onFromClick)
                MarginVertical(8.dp)
            }
            state.secondAddress?.let {
                AddressDisplay(it, endIconClick = callback::onToClick)
                MarginVertical(8.dp)
            }
            InfoTable(state.items)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
@Preview
fun TransferDetailsPreview() {

    val state = TransferDetailsState(
        id = TextInputViewState(
            text = "0xksduhf8747hrgouy2heqrfuh1erhf8037rfh8-139ujf",
            hint ="Hash",
            isActive = false,
            endIcon = R.drawable.ic_more_vertical
        ),
        firstAddress = AddressDisplayState(
            title = "From",
            input = "0xsjkdflsdgueroirgfosdifsd;fgoksd;fg;sd845tg849",
            image = jp.co.soramitsu.common.R.drawable.ic_address_placeholder,
            endIcon = R.drawable.ic_more_vertical
        ),
        secondAddress = AddressDisplayState(
            title = "To",
            input = "0xsjkdflsdgueroirgfosdifsd;fgoksd;fg;sd845tg849",
            image = jp.co.soramitsu.common.R.drawable.ic_address_placeholder,
            endIcon = R.drawable.ic_more_vertical
        ),
        status = OperationStatusAppearance.COMPLETED,
        items = listOf(
            TitleValueViewState(
                title = "Time",
                value = "29.10.2024, 18:01"
            ),
            TitleValueViewState(
                title = "Amount",
                value = "1.5 DOT"
            ),
            TitleValueViewState(
                title = "Transfer fee",
                value = "0.005342 DOT"
            ),
        )
    )

    TransferDetailScreen(state,
        object: TransactionDetailsCallbacks{
            override fun onNavigationClick() = Unit
            override fun onHashClick() = Unit
            override fun onFromClick() = Unit
            override fun onToClick() = Unit
        }
    )
}