package jp.co.soramitsu.wallet.impl.presentation.receive

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount

data class ReceiveScreenViewState(
    val qrCode: Bitmap,
    val assetSymbol: String,
    val account: WalletAccount
)

interface ReceiveScreenInterface {
    fun copyClicked()
    fun shareClicked()
}

@Composable
fun ReceiveScreen(
    state: LoadingState<ReceiveScreenViewState>,
    callback: ReceiveScreenInterface
) {
    BottomSheetScreen {
        when (state) {
            is LoadingState.Loading -> {}
            is LoadingState.Loaded -> {
                ReceiveContent(
                    state = state.data,
                    copyClicked = callback::copyClicked,
                    shareClicked = callback::shareClicked
                )
            }
        }
    }
}

@Composable
private fun ReceiveContent(
    state: ReceiveScreenViewState,
    copyClicked: () -> Unit,
    shareClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(Alignment.CenterHorizontally))
        MarginVertical(margin = 13.dp)
        H3(
            text = stringResource(id = R.string.wallet_asset_receive_template, state.assetSymbol),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        MarginVertical(margin = 40.dp)
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
            text = stringResource(
                id = R.string.common_middle_dots,
                state.account.address.take(10),
                state.account.address.takeLast(10)
            ),
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_info_14),
                alignment = Alignment.TopStart,
                contentDescription = null
            )
            MarginHorizontal(margin = 10.dp)
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        append(stringResource(id = R.string.common_note).uppercase())
                    }
                    append(" ")
                    withStyle(
                        style = SpanStyle(color = Color.White.copy(alpha = 0.5f))
                    ) {
                        append(stringResource(id = R.string.common_receive_alert_description))
                    }
                },
                style = MaterialTheme.customTypography.body2,
                modifier = Modifier.weight(1f)
            )
        }
        MarginVertical(margin = 12.dp)
    }
}
