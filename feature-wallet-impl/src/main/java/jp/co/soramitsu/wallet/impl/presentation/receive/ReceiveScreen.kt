package jp.co.soramitsu.wallet.impl.presentation.receive

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.receive.model.ReceiveToggleType

data class ReceiveScreenViewState(
    val qrCode: Bitmap,
    val assetSymbol: String,
    val account: WalletAccount,
    val multiToggleButtonState: MultiToggleButtonState<ReceiveToggleType>,
    val amountInputViewState: AmountInputViewState,
    val requestAllowed: Boolean
)

interface ReceiveScreenInterface {
    fun copyClicked()
    fun backClicked()
    fun shareClicked()
    fun tokenClicked()
    fun receiveChanged(type: ReceiveToggleType)
    fun onAmountInput(amount: BigDecimal?)
}

@Composable
fun ReceiveScreen(
    state: LoadingState<ReceiveScreenViewState>,
    callback: ReceiveScreenInterface
) {
    BottomSheetScreen(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .fillMaxWidth()
    ) {
        when (state) {
            is LoadingState.Loading -> {}
            is LoadingState.Loaded -> {
                ReceiveContent(
                    state = state.data,
                    copyClicked = callback::copyClicked,
                    shareClicked = callback::shareClicked,
                    backClicked = callback::backClicked,
                    receiveToggleChanged = callback::receiveChanged,
                    onAmountInput = callback::onAmountInput,
                    onTokenSelectClicked = callback::tokenClicked
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ReceiveContent(
    state: ReceiveScreenViewState,
    copyClicked: () -> Unit,
    shareClicked: () -> Unit,
    backClicked: () -> Unit,
    onTokenSelectClicked: () -> Unit,
    receiveToggleChanged: (ReceiveToggleType) -> Unit,
    onAmountInput: (BigDecimal?) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Toolbar(
        state = ToolbarViewState(
            title = stringResource(id = R.string.wallet_asset_receive_template, state.assetSymbol),
            navigationIcon = R.drawable.ic_arrow_back_24dp,
            menuItems = null
        ),
        onNavigationClick = backClicked
    )
    MarginVertical(margin = 16.dp)

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.requestAllowed) {
            MultiToggleButton(
                state = state.multiToggleButtonState,
                onToggleChange = receiveToggleChanged
            )
            MarginVertical(margin = 16.dp)
        }

        if (state.multiToggleButtonState.currentSelection == ReceiveToggleType.Request) {
            AmountInput(
                state = state.amountInputViewState,
                onInput = onAmountInput,
                onTokenClick = {
                    keyboardController?.hide()
                    onTokenSelectClicked()
                },
                onKeyboardDone = { keyboardController?.hide() }
            )
            MarginVertical(margin = 16.dp)
        }

        Surface(
            color = Color.Unspecified,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(16.dp))
                .size(240.dp)
                .padding(20.dp)
                .wrapContentSize()
        ) {
            Image(
                bitmap = state.qrCode.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
        }
        MarginVertical(margin = 24.dp)
        H2(text = state.account.name.orEmpty())
        MarginVertical(margin = 8.dp)
        B0(
            text = state.account.address.shortenAddress(),
            maxLines = 1,
            color = Color.White.copy(alpha = 0.5f)
        )
        MarginVertical(margin = 24.dp)
        AccentButton(
            text = stringResource(id = R.string.common_copy),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            onClick = copyClicked
        )
        MarginVertical(margin = 12.dp)
        GrayButton(
            text = stringResource(id = R.string.common_share),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            onClick = shareClicked
        )
        MarginVertical(margin = 12.dp)
    }
}

@Preview
@Composable
private fun ReceiveScreenPreview() {
    ReceiveScreen(
        state = LoadingState.Loaded(
            data = ReceiveScreenViewState(
                Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888),
                assetSymbol = "SMBL",
                account = WalletAccount("address", "wallet name"),
                multiToggleButtonState = MultiToggleButtonState(
                    currentSelection = ReceiveToggleType.Request,
                    toggleStates = ReceiveToggleType.values().toList()
                ),
                amountInputViewState = AmountInputViewState(
                    totalBalance = "totalBalance",
                    fiatAmount = null,
                    tokenAmount = BigDecimal.ONE
                ),
                requestAllowed = true
            )
        ),
        callback = object : ReceiveScreenInterface {
            override fun copyClicked() {}
            override fun backClicked() {}
            override fun shareClicked() {}
            override fun tokenClicked() {}
            override fun receiveChanged(type: ReceiveToggleType) {}
            override fun onAmountInput(amount: BigDecimal?) {}
        }
    )
}