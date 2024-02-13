package jp.co.soramitsu.nft.impl.presentation.confirmsend

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.retrievePainter
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendCallback
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendScreenState
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import kotlinx.coroutines.flow.Flow

@Suppress("FunctionName")
fun NavGraphBuilder.ConfirmNFTSendNavComposable(
    stateFlow: Flow<ConfirmNFTSendScreenState>,
    callback: ConfirmNFTSendCallback
) {
    composable(NFTNavGraphRoute.ConfirmNFTSendScreen.routeName) {
        val state = stateFlow.collectAsStateWithLifecycle(ConfirmNFTSendScreenState.default)

        ConfirmNFTSendScreen(
            state = state.value,
            callback = callback
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConfirmNFTSendScreen(state: ConfirmNFTSendScreenState, callback: ConfirmNFTSendCallback) {
    val keyboardController = LocalSoftwareKeyboardController.current
    FullScreenLoading(
        isLoading = state.isLoading,
        contentAlignment = Alignment.BottomStart
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                when (val thumbnailModel = state.thumbnailImageModel) {
                    is Loadable.InProgress ->
                        GradientIcon(
                            iconRes = R.drawable.ic_fearless_logo,
                            color = colorAccentDark,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .shimmer()
                        )

                    is Loadable.ReadyToRender ->
                        Image(
                            painter = thumbnailModel.data.retrievePainter(),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                }
                MarginVertical(margin = 16.dp)

                H2(
                    text = stringResource(id = R.string.sending),
                    color = black2,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 24.dp)

                InfoTable(
                    items = state.tableItems,
                    onItemClick = callback::onItemClick
                )
                MarginVertical(margin = 12.dp)

                Spacer(modifier = Modifier.weight(1f))

                val isInitialLoading = state.feeInfoItem == null
                AccentButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    text = state.buttonState.text.takeIf { isInitialLoading.not() }.orEmpty(),
                    enabled = state.buttonState.enabled,
                    loading = isInitialLoading,
                    onClick = {
                        keyboardController?.hide()
                        callback.onConfirmClick()
                    }
                )

                MarginVertical(margin = 12.dp)
            }
        }
    }
}

@Preview
@Composable
private fun ConfirmSendPreview() {
    val state = ConfirmNFTSendScreenState(
        thumbnailImageModel = Loadable.ReadyToRender(
            ImageModel.ResId(R.drawable.drawable_fearless_bird)
        ),
        fromInfoItem = TitleValueViewState(
            title = "From",
            value = "My Awesome Wallet",
            additionalValue = "EBN4KURhvkEBN4KURhvkEBN4KURhvkEBN4KURhvk"
        ),
        toInfoItem = TitleValueViewState(
            title = "To",
            value = "EBN4KURhvkEBN4KURhvkEBN4KURhvkEBN4KURhvk"
        ),
        collectionInfoItem = TitleValueViewState(
            title = "Collection",
            value = "Bird1 #1"
        ),
        feeInfoItem = TitleValueViewState(
            title = "Fee",
            value = "3 ETH",
            additionalValue = "\$5,05"
        ),
        buttonState = ButtonViewState("Continue", true),
        isLoading = false
    )

    FearlessTheme {
        BottomSheetScreen {
            ConfirmNFTSendScreen(
                state = state,
                callback = ConfirmNFTSendCallback
            )
        }
    }
}
