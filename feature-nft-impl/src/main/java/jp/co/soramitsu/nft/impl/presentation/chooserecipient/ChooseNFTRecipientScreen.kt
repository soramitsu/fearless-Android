package jp.co.soramitsu.nft.impl.presentation.chooserecipient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import jp.co.soramitsu.common.compose.component.AccentDarkDisabledButton
import jp.co.soramitsu.common.compose.component.AddressInput
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.Badge
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientCallback
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientScreenState
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import kotlinx.coroutines.flow.Flow

@Suppress("FunctionName")
fun NavGraphBuilder.ChooseNFTRecipientNavComposable(
    stateFlow: Flow<ChooseNFTRecipientScreenState>,
    callback: ChooseNFTRecipientCallback
) {
    composable(NFTNavGraphRoute.ChooseNFTRecipientScreen.routeName) {
        val state = stateFlow.collectAsStateWithLifecycle(ChooseNFTRecipientScreenState.default)

        ChooseNFTRecipientScreen(
            state = state.value,
            callback = callback
        )
    }
}

@Composable
private fun ChooseNFTRecipientScreen(state: ChooseNFTRecipientScreenState, callback: ChooseNFTRecipientCallback) {
    FullScreenLoading(isLoading = state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                MarginVertical(margin = 16.dp)
                AddressInput(
                    state = state.addressInputState,
                    onInput = callback::onAddressInput,
                    onInputClear = callback::onAddressInputClear,
                    onPaste = callback::onPasteClick
                )

                MarginVertical(margin = 8.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Badge(
                        modifier = Modifier.wrapContentSize(),
                        iconResId = R.drawable.ic_scan,
                        labelResId = R.string.chip_qr,
                        onClick = callback::onQrClick
                    )
                    if (state.isHistoryAvailable) {
                        Badge(
                            modifier = Modifier.wrapContentSize(),
                            iconResId = R.drawable.ic_history_16,
                            labelResId = R.string.chip_history,
                            onClick = callback::onHistoryClick
                        )
                    }
                    Badge(
                        modifier = Modifier
                            .wrapContentSize(Alignment.CenterEnd)
                            .weight(1f),
                        icon = state.selectedWalletIcon,
                        labelResId = R.string.chip_my_wallets,
                        onClick = callback::onWalletsClick
                    )
                }

                MarginVertical(margin = 12.dp)
                FeeInfo(
                    state = state.feeInfoState,
                    modifier = Modifier.defaultMinSize(minHeight = 52.dp)
                )

                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .background(backgroundBlack.copy(alpha = 0.75f))
                    .align(Alignment.BottomCenter)
            ) {
                AccentDarkDisabledButton(
                    state = state.buttonState,
                    onClick = callback::onNextClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp)
                        .height(48.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun SendSetupPreview() {
    val state = ChooseNFTRecipientScreenState(
        selectedWalletIcon = null,
        addressInputState = AddressInputState("Send to", "", ""),
        buttonState = ButtonViewState("Continue", true),
        isHistoryAvailable = false,
        feeInfoState = FeeInfoViewState.default,
        isLoading = false
    )

    FearlessTheme {
        BottomSheetScreen {
            ChooseNFTRecipientScreen(
                state = state,
                callback = ChooseNFTRecipientCallback
            )
        }
    }
}
