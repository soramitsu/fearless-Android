package jp.co.soramitsu.success.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

data class SuccessViewState(
    val title: String,
    val message: String,
    val tableItems: List<TitleValueViewState>,
    val explorer: Pair<Chain.Explorer.Type, String>?
) {
    companion object {
        const val CODE_HASH_CLICK = 2
        val default = SuccessViewState("", "", emptyList(), null)
    }
}

interface SuccessScreenInterface {
    fun onClose()
    fun onItemClick(code: Int)
    fun onExplorerClick()
    fun onShareClick()
}

@Composable
fun SuccessContent(
    state: SuccessViewState,
    callback: SuccessScreenInterface
) {
    BottomSheetScreen {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                IconButton(
                    onClick = callback::onClose,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(CircleShape)
                        .background(backgroundBlurColor)
                        .size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        tint = white,
                        contentDescription = null
                    )
                }
                MarginVertical(margin = 20.dp)
                GradientIcon(
                    iconRes = R.drawable.ic_fearless_logo,
                    color = colorAccentDark,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 16.dp)
                H2(
                    text = state.title,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 8.dp)
                B0(
                    text = state.message,
                    color = black2,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center
                )
                MarginVertical(margin = 24.dp)
                if (state.tableItems.isNotEmpty()) {
                    InfoTable(
                        items = state.tableItems,
                        onItemClick = callback::onItemClick
                    )
                }
                val knownExplorer = state.explorer?.takeIf { it.first != Chain.Explorer.Type.UNKNOWN }
                if (knownExplorer != null) {
                    MarginVertical(margin = 16.dp)
                    Row {
                        GrayButton(
                            text = knownExplorer.first.capitalizedName,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            onClick = callback::onExplorerClick
                        )
                        MarginHorizontal(margin = 12.dp)
                        AccentButton(
                            text = stringResource(R.string.common_share),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            onClick = callback::onShareClick
                        )
                    }
                } else {
                    MarginVertical(margin = 16.dp)
                    AccentButton(
                        text = stringResource(id = R.string.common_close),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        onClick = callback::onClose
                    )
                }
                MarginVertical(margin = 16.dp)
            }
        }
    }
}

@Preview
@Composable
private fun SuccessPreview() {
    val state = SuccessViewState(
        title = "Title HERE",
        message = "Your transaction has been successfully sent to blockchain",
        tableItems = listOf(
            TitleValueViewState(
                title = "Hash",
                value = "EBN4KURhvkEBN4KURhvkEBN4KURhvkEBN4KURhvk",
                clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_copy_filled_24, 1)
            ),
            TitleValueViewState(
                title = "Result",
                value = "Success",
                valueColor = greenText
            )
        ),
//        explorer = Chain.Explorer.Type.SUBSCAN to "url"
        explorer = Chain.Explorer.Type.UNKNOWN to "url"
    )

    val emptyCallback = object : SuccessScreenInterface {
        override fun onClose() {}
        override fun onItemClick(code: Int) {}
        override fun onExplorerClick() {}
        override fun onShareClick() {}
    }

    FearlessTheme {
        SuccessContent(
            state = state,
            callback = emptyCallback
        )
    }
}
