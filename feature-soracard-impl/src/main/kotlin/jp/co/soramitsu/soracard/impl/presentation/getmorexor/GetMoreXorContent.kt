package jp.co.soramitsu.soracard.impl.presentation.getmorexor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.feature_soracard_impl.R

interface GetMoreXorScreenInterface {
    fun onSwapForXorClick()
    fun onBuyXorClick()
    fun onBackClicked()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GetMoreXorContent(
    callback: GetMoreXorScreenInterface
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    BottomSheetScreen {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    tint = white,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.End)
                        .clickable(onClick = callback::onBackClicked)

                )
                MarginVertical(margin = 44.dp)

                GradientIcon(
                    iconRes = R.drawable.ic_warning_filled,
                    color = warningOrange,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 16.dp)
                H3(
                    text = stringResource(id = R.string.sora_card_get_more_xor),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 8.dp)
                B0(
                    text = stringResource(id = R.string.sora_card_swap_or_buy_xor),
                    color = white50,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 12.dp)

                AccentButton(
                    text = stringResource(id = R.string.sora_card_buy_xor),
                    onClick = {
                        keyboardController?.hide()
                        callback.onBuyXorClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
                MarginVertical(margin = 12.dp)

                GrayButton(
                    text = stringResource(id = R.string.sora_card_swap_for_xor),
                    onClick = {
                        keyboardController?.hide()
                        callback.onSwapForXorClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )

                MarginVertical(margin = 12.dp)
            }
        }
    }
}

@Preview
@Composable
private fun GetMoreXorPreview() {
    val emptyCallback = object : GetMoreXorScreenInterface {
        override fun onBackClicked() {}
        override fun onSwapForXorClick() {}
        override fun onBuyXorClick() {}
    }

    FearlessTheme {
        GetMoreXorContent(
            callback = emptyCallback
        )
    }
}
