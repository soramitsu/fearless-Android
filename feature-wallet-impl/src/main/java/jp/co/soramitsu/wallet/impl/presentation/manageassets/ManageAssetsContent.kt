package jp.co.soramitsu.wallet.impl.presentation.manageassets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.darkButtonBackground
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.utils.clickableSingle
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class ManageAssetsScreenViewState(
    val assets: Map<String, List<ManageAssetItemState>>? = null,
    val selectedChainTitle: String = "",
    val selectedAssetId: String? = null,
    val searchQuery: String? = null,
    val showAllChains: Boolean = true
) {
    companion object {
        val default = ManageAssetsScreenViewState()
    }
}

interface ManageAssetsContentInterface {
    fun onSearchInput(input: String)
    fun onChecked(assetItemState: ManageAssetItemState, checked: Boolean)
    fun onItemClicked(assetItemState: ManageAssetItemState)
    fun onEditClicked(assetItemState: ManageAssetItemState)
    fun onDoneClicked()
    fun onSelectedChainClicked()
}

@Composable
fun ManageAssetsContent(
    state: ManageAssetsScreenViewState,
    callback: ManageAssetsContentInterface
) {
    Column(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            B0(
                text = stringResource(id = R.string.common_done),
                modifier = Modifier
                    .align(CenterVertically)
                    .clickableSingle(onClick = callback::onDoneClicked),
                color = colorAccentDark
            )
            B0(
                text = state.selectedChainTitle,
                modifier = Modifier
                    .align(CenterVertically)
                    .clickableSingle(onClick = callback::onSelectedChainClicked)
            )
        }
        MarginVertical(margin = 16.dp)
        CorneredInput(state = state.searchQuery, onInput = callback::onSearchInput, hintLabel = stringResource(id = R.string.common_search))
        MarginVertical(margin = 8.dp)

        if (state.assets == null) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                FearlessProgress(
                    Modifier.align(Alignment.Center)
                )
            }
        } else if (state.assets.isEmpty()) {
            MarginVertical(margin = 16.dp)
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .align(CenterHorizontally)
            ) {
                EmptyResultContent()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    ManageAssetsHeader()
                }

                items(state.assets.entries.toList()) { assetsGroup ->
                    val assets = assetsGroup.value

                    if (assets.size == 1) {
                        ManageAssetItem(assets[0], callback::onEditClicked, callback::onItemClicked, callback::onChecked)
                    } else {
                        val isCollapsed = remember { mutableStateOf(true) }

                        LaunchedEffect(key1 = state.searchQuery) {
                            if (state.searchQuery.isNullOrBlank().not()) {
                                isCollapsed.value = false
                            }
                        }

                        GroupItem(assets, isCollapsed)

                        if (isCollapsed.value.not()) {
                            Column {
                                assets.map {
                                    ManageAssetItem(it.copy(isGrouped = true), callback::onEditClicked, callback::onItemClicked, callback::onChecked)
                                }
                            }
                        }
                    }
                }
            }
        }
        MarginVertical(margin = 52.dp)
    }
}

@Composable
private fun ManageAssetsHeader() {
    Box(
        modifier = Modifier
            .height(44.dp)
            .fillMaxWidth(),
        contentAlignment = CenterStart
    ) {
        H5(
            text = stringResource(id = R.string.wallet_manage_assets),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
fun EmptyResultContent() {
    Column(
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_alert),
            contentDescription = null,
            tint = gray2
        )
        H3(text = stringResource(id = R.string.common_search_assets_alert_title))
        B0(
            text = stringResource(id = R.string.common_empty_search),
            color = gray2
        )
    }
}

data class ManageAssetItemState(
    val id: String,
    val imageUrl: String?,
    val chainName: String,
    val assetName: String?,
    val symbol: String,
    val amount: String,
    val fiatAmount: String?,
    val chainId: ChainId,
    val isChecked: Boolean,
    val showEdit: Boolean,
    val isZeroAmount: Boolean,
    val isGrouped: Boolean = false
)

@Composable
fun ManageAssetItem(
    state: ManageAssetItemState,
    onEditClick: (ManageAssetItemState) -> Unit,
    onItemClick: (ManageAssetItemState) -> Unit,
    onChecked: (ManageAssetItemState, Boolean) -> Unit
) {
    val switchColors = object : SwitchColors {
        @Composable
        override fun thumbColor(enabled: Boolean, checked: Boolean): State<Color> {
            val color = if (enabled) {
                white
            } else {
                white64
            }
            return rememberUpdatedState(color)
        }

        @Composable
        override fun trackColor(enabled: Boolean, checked: Boolean): State<Color> {
            return rememberUpdatedState(transparent)
        }
    }

    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .background(if (state.isGrouped) darkButtonBackground else Color.Unspecified)
            .clickableWithNoIndication {
                onItemClick(state)
            }
    ) {
        AsyncImage(
            model = state.imageUrl?.let { getImageRequest(LocalContext.current, it) },
            contentDescription = null,
            modifier = Modifier
                .testTag("ManageAssetItem_image_${state.id}")
                .size(32.dp),
            colorFilter = ColorFilter.tint(white64, BlendMode.DstOut).takeIf { state.isChecked.not() }
        )
        MarginHorizontal(margin = 10.dp)
        Row(
            verticalAlignment = CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = CenterVertically
                ) {
                    val symbolColor = if (!state.isChecked || state.isZeroAmount) black2 else Color.Unspecified
                    B1(
                        text = state.symbol,
                        fontWeight = FontWeight.W600,
                        color = symbolColor
                    )
                    if (state.showEdit) {
                        MarginHorizontal(margin = 6.dp)
                        Image(
                            res = R.drawable.ic_edit_20,
                            modifier = Modifier
                                .size(20.dp)
                                .clickableSingle {
                                    onEditClick(state)
                                }
                        )
                    }
                }

                B2(text = state.chainName, color = black2)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.isChecked) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    B1(
                        text = state.amount,
                        fontWeight = FontWeight.W600
                    )
                    state.fiatAmount?.let {
                        B2(text = it, color = black2)
                    }
                }
            }
            MarginHorizontal(margin = 8.dp)
            val trackColor = when {
                state.isChecked -> colorAccentDark
                else -> black3
            }
            Switch(
                colors = switchColors,
                checked = state.isChecked,
                onCheckedChange = { onChecked(state, it) },
                modifier = Modifier
                    .background(color = trackColor, shape = RoundedCornerShape(20.dp))
                    .padding(3.dp)
                    .height(20.dp)
                    .width(36.dp)
                    .align(CenterVertically)
            )
        }
    }
}

@Composable
private fun GroupItem(
    groupAssets: List<ManageAssetItemState>,
    isCollapsed: MutableState<Boolean>
) {
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .clickableWithNoIndication {
                isCollapsed.value = isCollapsed.value.not()
            }
    ) {
        val image = groupAssets.firstOrNull { it.imageUrl != null }?.imageUrl?.let {
            getImageRequest(LocalContext.current, it)
        }
        val assetName = groupAssets.firstOrNull { it.assetName != null }?.assetName.orEmpty()
        val allAssetsAreHidden = groupAssets.all { it.isChecked.not() }

        AsyncImage(
            model = image,
            contentDescription = null,
            modifier = Modifier
                .testTag("ManageGroupItem_image_${groupAssets.getOrNull(0)?.symbol}")
                .size(32.dp),
            colorFilter = ColorFilter.tint(white64, BlendMode.DstOut).takeIf { allAssetsAreHidden }
        )
        MarginHorizontal(margin = 10.dp)
        Row(
            verticalAlignment = CenterVertically
        ) {
            val groupNameColor = if (allAssetsAreHidden) black2 else Color.Unspecified

            Column {
                B1(text = assetName, fontWeight = FontWeight.W600, color = groupNameColor)
                B2(text = pluralStringResource(id = R.plurals.common_networks_format, groupAssets.size, groupAssets.size), color = black2)
            }
            Spacer(modifier = Modifier.weight(1f))
            Image(
                res = R.drawable.ic_chevron_up_white,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (isCollapsed.value) 180f else 0f)
            )
            MarginHorizontal(margin = 8.dp)
        }
    }
}

@Preview
@Composable
private fun ManageAssetsScreenPreview() {
    val items = listOf(
        ManageAssetItemState(
            id = "1",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
            chainName = "Kusama",
            assetName = "Asset on Kusama",
            symbol = "KSM",
            amount = "0",
            fiatAmount = "0$",
            chainId = "",
            isChecked = true,
            isZeroAmount = true,
            showEdit = false
        ),
        ManageAssetItemState(
            id = "2",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Kusama.svg",
            chainName = "Moonriver",
            assetName = "Asset on Moonriver",
            symbol = "MOVR",
            amount = "10",
            fiatAmount = "23240$",
            chainId = "",
            isChecked = false,
            isZeroAmount = true,
            showEdit = true
        ),
        ManageAssetItemState(
            id = "3",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Kusama.svg",
            chainName = "Westend",
            assetName = "WND from the Westend",
            symbol = "WND",
            amount = "42",
            fiatAmount = null,
            chainId = "",
            isChecked = true,
            isZeroAmount = true,
            showEdit = true
        ),
        ManageAssetItemState(
            id = "4",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Kusama.svg",
            chainName = "TWO-TEE",
            assetName = "TWO TEE TO TWO-TWO",
            symbol = "TWO",
            amount = "333",
            fiatAmount = null,
            chainId = "",
            isChecked = false,
            isZeroAmount = true,
            showEdit = true
        )
    )
    val state = ManageAssetsScreenViewState(
        selectedChainTitle = "All chains",
        assets = mapOf(
            "DOT" to items,
            "disabled assets" to items.filter { it.isChecked.not() },
            "MOVR" to items.filter { it.symbol == "MOVR" },
            "KSM" to items.filter { it.symbol == "KSM" },
            "WND" to items.filter { it.symbol == "WND" },
            ),
        searchQuery = null
    )
    ManageAssetItem(items[0], {}, {}, { _, _ -> })
    Column(
        Modifier.background(black4)
    ) {
        ManageAssetsContent(
            state = state,
            callback = object : ManageAssetsContentInterface {
                override fun onSearchInput(input: String) {}
                override fun onChecked(assetItemState: ManageAssetItemState, checked: Boolean) {}
                override fun onItemClicked(assetItemState: ManageAssetItemState) {}
                override fun onEditClicked(assetItemState: ManageAssetItemState) {}
                override fun onDoneClicked() {}
                override fun onSelectedChainClicked() {}
            }
        )
    }
}
