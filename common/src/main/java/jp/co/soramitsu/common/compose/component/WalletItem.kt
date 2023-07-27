package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.borderGradientColors
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class WalletItemViewState(
    val id: Long,
    val balance: String? = null,
    val assetSymbol: String? = null,
    val changeBalanceViewState: ChangeBalanceViewState? = null,
    val title: String,
    val walletIcon: Any,
    val isSelected: Boolean,
    val additionalMetadata: String = ""
)

@Composable
fun WalletItem(
    state: WalletItemViewState,
    onOptionsClick: ((WalletItemViewState) -> Unit)? = null,
    onSelected: (WalletItemViewState) -> Unit,
    onLongClick: (WalletItemViewState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = if (state.isSelected) {
        borderGradientColors
    } else {
        listOf(white08)
    }

    BackgroundCorneredWithGradientBorder(
        modifier = modifier
            .fillMaxWidth()
            .clickableWithNoIndication { onSelected(state) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { /* Called when the gesture starts */ },
                    onDoubleTap = { /* Called on Double Tap */ },
                    onLongPress = {
                        /* Called on Long Press */
                        onLongClick(state)
                    },
                    onTap = {
                        /* Called on Tap */
                        onSelected(state)
                    }
                )
            },
        borderColors = borderColor,
        backgroundColor = black05 // white08.compositeOver(darkButtonBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .size(32.dp)
            ) {
                Icon(
                    modifier = Modifier.fillMaxSize(),
                    painter = rememberAsyncImagePainter(model = state.walletIcon),
                    contentDescription = null,
                    tint = Color.Unspecified
                )
            }
            MarginHorizontal(margin = 12.dp)
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                if (state.balance != null || state.changeBalanceViewState != null) {
                    B2(
                        text = state.title,
                        color = gray2
                    )
                    H4(
                        text = state.assetSymbol.orEmpty() + state.balance.orEmpty()
                    )
                    state.changeBalanceViewState?.let {
                        ChangeBalance(state = it)
                    }
                } else {
                    B1(
                        text = state.title,
                        color = gray2
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            onOptionsClick?.let { optionsAction ->
                Box(
                    contentAlignment = Alignment.CenterEnd
                ) {
                    IconButton(
                        onClick = {
                            optionsAction(state)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(30.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dots_horizontal_24),
                            tint = Color.Unspecified,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun WalletItemPreview() {
    val percentChange = "+5.67%"
    val assetBalanceFiat = "$2345.32"
    val assetBalance = "44400.3"
    val assetSymbol = "$"
    val walletTitle = "My Wallet"
    val isSelected = true

    val changeBalanceViewState = ChangeBalanceViewState(
        percentChange = percentChange,
        fiatChange = assetBalanceFiat
    )

    val state = WalletItemViewState(
        id = 111,
        balance = assetBalance,
        assetSymbol = assetSymbol,
        title = walletTitle,
        walletIcon = R.drawable.ic_wallet,
        isSelected = isSelected,
        changeBalanceViewState = changeBalanceViewState
    )

    Column {
        WalletItem(
            state = state,
            onOptionsClick = {},
            onSelected = {}
        )
        WalletItem(
            state = state.copy(isSelected = false),
            onOptionsClick = {},
            onSelected = {}
        )
        WalletItem(
            state = state.copy(
                isSelected = false,
                changeBalanceViewState = null,
                balance = null
            ),
            onSelected = {}
        )
    }
}
