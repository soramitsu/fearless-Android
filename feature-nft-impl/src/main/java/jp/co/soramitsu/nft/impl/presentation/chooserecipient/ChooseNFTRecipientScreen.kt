package jp.co.soramitsu.nft.impl.presentation.chooserecipient

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import jp.co.soramitsu.common.compose.component.AccentDarkDisabledButton
import jp.co.soramitsu.common.compose.component.AddressInput
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.CapsTitle
import jp.co.soramitsu.common.compose.component.ColoredButton
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.impl.navigation.Destination
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientCallback
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientScreenState
import kotlinx.coroutines.flow.Flow

@Suppress("FunctionName")
fun NavGraphBuilder.ChooseNFTRecipientNavComposable(
    stateFlow: Flow<ChooseNFTRecipientScreenState>,
    callback: ChooseNFTRecipientCallback
) {
    composable(Destination.NestedNavGraphRoute.ChooseNFTRecipientScreen.routeName) {
        val state = stateFlow.collectAsStateWithLifecycle(ChooseNFTRecipientScreenState.default)

        ChooseNFTRecipientScreen(
            state = state.value,
            callback = callback
        )
    }
}

@Composable
private fun ChooseNFTRecipientScreen(
    state: ChooseNFTRecipientScreenState,
    callback: ChooseNFTRecipientCallback
) {
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
                        iconResId = R.drawable.ic_wallet,
                        labelResId = R.string.chip_my_wallets,
                        onClick = callback::onWalletsClick
                    )
                }

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

@Composable
private fun Badge(
    modifier: Modifier = Modifier,
    @DrawableRes iconResId: Int,
    @StringRes labelResId: Int,
    onClick: () -> Unit
) {
    ColoredButton(
        modifier = modifier,
        backgroundColor = black05,
        border = BorderStroke(1.dp, white24),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            tint = Color.White,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        MarginHorizontal(margin = 4.dp)
        CapsTitle(text = stringResource(id = labelResId))
    }
}


@Preview
@Composable
private fun SendSetupPreview() {
    val state = ChooseNFTRecipientScreenState(
        addressInputState = AddressInputState("Send to", "", ""),
        buttonState = ButtonViewState("Continue", true),
        isHistoryAvailable = false,
        isLoading = true
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