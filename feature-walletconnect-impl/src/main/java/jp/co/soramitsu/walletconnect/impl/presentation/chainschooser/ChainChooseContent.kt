package jp.co.soramitsu.walletconnect.impl.presentation.chainschooser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id

data class ChainSelectScreenViewState(
    val chains: List<ChainItemState>?,
    val searchQuery: String? = null,
    val isViewMode: Boolean = false
) {
    companion object {
        val default = ChainSelectScreenViewState(emptyList())
    }
}

@Composable
fun ChainSelectContent(
    state: ChainSelectScreenViewState,
    onChainSelected: (chainItemState: ChainItemState?) -> Unit = {},
    onSearchInput: (input: String) -> Unit = {},
    onSelectAllClicked: () -> Unit = {},
    onDoneClicked: () -> Unit = {},
) {
    BottomSheetScreen(
        modifier = Modifier
            .padding(top = 106.dp)
    ) {
        Column(
            modifier = Modifier
                .nestedScroll(rememberNestedScrollInteropConnection())
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .imePadding()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isViewMode.not()) {
                    val manageAllText = if (state.chains?.any { it.isSelected.not()} == true) {
                        stringResource(id = R.string.common_select_all)
                    } else {
                        stringResource(id = R.string.staking_custom_deselect_button_title)
                    }
                    B0(
                        modifier = Modifier
                            .align(Alignment.Companion.CenterStart)
                            .clickableWithNoIndication {
                                onSelectAllClicked()
                            },
                        text = manageAllText,
                        color = colorAccentDark
                    )
                }
                val selectedAmount = state.chains?.filter { it.isSelected }.orEmpty().size
                H3(
                    modifier = Modifier.align(Alignment.Companion.Center),
                    text = stringResource(id = R.string.common_selected) + ": $selectedAmount"
                )
                B0(
                    modifier = Modifier
                        .align(Alignment.Companion.CenterEnd)
                        .clickableWithNoIndication {
                            onDoneClicked()
                        },
                    text = stringResource(id = R.string.common_done)
                )
            }
            MarginVertical(margin = 16.dp)
            CorneredInput(state = state.searchQuery, onInput = onSearchInput)
            when {
                state.chains == null -> {}
                state.chains.isEmpty() -> {
                    MarginVertical(margin = 16.dp)
                    Column(
                        horizontalAlignment = CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .align(CenterHorizontally)
                    ) {
                        EmptyResultContent()
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.chains) { chain ->
                            ChainItem(
                                state = chain,
                                onSelected = {
                                    if (state.isViewMode.not()) {
                                        onChainSelected(it)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            MarginVertical(margin = 16.dp)
        }
    }
}

data class ChainItemState(
    val caip2id: String,
    val imageUrl: String?,
    val title: String,
    val isSelected: Boolean = false
)

fun Chain.toChainItemState(isSelected: Boolean) = ChainItemState(
    caip2id = caip2id,
    imageUrl = icon,
    title = name,
    isSelected = isSelected
)

@Composable
fun EmptyResultContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )

        H3(text = stringResource(id = R.string.common_search_assets_alert_title))
        B0(
            text = stringResource(id = R.string.common_empty_search),
            color = white50
        )
    }
}

@Composable
fun ChainItem(state: ChainItemState, onSelected: (ChainItemState?) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .clickableWithNoIndication { onSelected(state) }
    ) {
        val selectionIconRes = if (state.isSelected) {
            R.drawable.ic_selected
        } else {
            R.drawable.ic_selected_not
        }
        Image(
            res = selectionIconRes,
            modifier = Modifier
                .testTag("ChainItem_image_selected")
                .size(24.dp)
        )

        MarginHorizontal(margin = 16.dp)

        AsyncImage(
            model = state.imageUrl?.let { getImageRequest(LocalContext.current, it) },
            contentDescription = null,
            modifier = Modifier
                .testTag("ChainItem_image_${state.caip2id}")
                .size(24.dp)
        )
        MarginHorizontal(margin = 10.dp)
        H4(text = state.title)
    }
}

@Preview
@Composable
private fun SelectChainScreenPreview() {
    val items = listOf(
        ChainItemState(
            caip2id = "1",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
            title = "Kusama",
            isSelected = true
        ),
        ChainItemState(
            caip2id = "2",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Kusama.svg",
            title = "Moonriver"
        )
    )
    val state = ChainSelectScreenViewState(
        chains = items,
        searchQuery = null
    )
    Column(
        Modifier.background(black4)
    ) {
        ChainSelectContent(state = state)
    }
}
