package jp.co.soramitsu.wallet.impl.presentation.manageassets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import jp.co.soramitsu.common.compose.theme.alertYellow
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
    val allSectionAssets: Map<String, List<ManageAssetItemState>> = emptyMap(),
    val selectedChainTitle: String = "",
    val selectedAssetId: String? = null,
    val searchQuery: String? = null,
    val showAllChains: Boolean = true,
    val showTestnets: Boolean = false,
    val visibilityFilter: ManageAssetFilter = ManageAssetFilter.All,
    val collapsedSections: Set<String> = emptySet(),
    val lens: jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioLens = jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioLens.ByToken
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
    fun onFilterChanged(filter: ManageAssetFilter) {}
    fun onSectionToggle(id: String) {}
    fun onGroupChecked(section: String, checked: Boolean) {}
    fun onShowTestnetsChanged(show: Boolean)
}

@Composable
fun ManageAssetsContent(
    state: ManageAssetsScreenViewState,
    callback: ManageAssetsContentInterface
) {
    Column(Modifier.nestedScroll(rememberNestedScrollInteropConnection()).padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
            B0(text = stringResource(R.string.common_done), color = colorAccentDark,
                modifier = Modifier.heightIn(min = 48.dp).clickableSingle(onClick = callback::onDoneClicked))
            B0(text = state.selectedChainTitle, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = 16.dp).heightIn(min = 48.dp)
                    .clickableSingle(onClick = callback::onSelectedChainClicked))
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "manage-controls") {
                MarginVertical(16.dp)
                CorneredInput(state = state.searchQuery, onInput = callback::onSearchInput, hintLabel = stringResource(R.string.common_search))
                MarginVertical(8.dp)
                jp.co.soramitsu.common.compose.component.MultiToggleButton(
                    jp.co.soramitsu.common.compose.component.MultiToggleButtonState(state.visibilityFilter, ManageAssetFilter.entries),
                    callback::onFilterChanged, stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f)
                MarginVertical(8.dp)
                ShowTestnetsRow(isChecked = state.showTestnets, onCheckedChange = callback::onShowTestnetsChanged)
                ManageAssetsHeader()
            }
            when {
                state.assets == null -> item { Box(Modifier.fillMaxWidth().height(120.dp)) { FearlessProgress(Modifier.align(Alignment.Center)) } }
                state.assets.isEmpty() -> item {
                    if (state.visibilityFilter == ManageAssetFilter.Hidden) B1(text = stringResource(R.string.portfolio_no_hidden))
                    else EmptyResultContent()
                }
                else -> state.assets.forEach { (section, assets) ->
                    if (!section.startsWith("group:") && !section.startsWith("network:")) {
                        items(assets, key = { it.canonicalAssetKey }) {
                            ManageAssetItem(it, callback::onEditClicked, callback::onItemClicked, callback::onChecked)
                        }
                    } else {
                        val collapsed = section in state.collapsedSections && state.searchQuery.isNullOrBlank()
                        item(key = "header:$section") {
                            GroupItem(state.allSectionAssets[section] ?: assets, collapsed, section.startsWith("network:"),
                                { callback.onSectionToggle(section) }, { callback.onGroupChecked(section, it) })
                        }
                        if (!collapsed) items(assets, key = { it.canonicalAssetKey }) {
                            ManageAssetItem(it.copy(isGrouped = true), callback::onEditClicked, callback::onItemClicked, callback::onChecked)
                        }
                    }
                }
            }
            item(key = "manage-bottom") { MarginVertical(52.dp) }
        }
    }
}

@Composable
private fun ShowTestnetsRow(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ManageAssets_show_testnets"),
        verticalAlignment = CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            B1(text = stringResource(id = R.string.portfolio_show_testnets))
            B2(text = stringResource(id = R.string.portfolio_show_testnets_description), color = gray2)
        }
        MarginHorizontal(margin = 8.dp)
        ManageAssetsSwitch(checked = isChecked, onCheckedChange = onCheckedChange)
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
    val canonicalAssetKey: String,
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
    val isGrouped: Boolean = false,
    /**
     * Set for a hidden asset that holds a balance, for example "Hidden in previous version". Such
     * a row keeps its amount visible although its switch is off.
     */
    val hiddenNote: String? = null,
    val preference: String = "auto",
    val isTestnet: Boolean = false,
    val isFavorite: Boolean = false,
    val isRankedMainnet: Boolean = false
)

@Composable
fun ManageAssetItem(
    state: ManageAssetItemState,
    onEditClick: (ManageAssetItemState) -> Unit,
    onItemClick: (ManageAssetItemState) -> Unit,
    onChecked: (ManageAssetItemState, Boolean) -> Unit
) {
    Row(verticalAlignment = CenterVertically, modifier = Modifier.fillMaxWidth()
        .background(if (state.isGrouped) darkButtonBackground else Color.Unspecified).padding(vertical = 10.dp)) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            B1(text = state.symbol, fontWeight = FontWeight.W600)
            B2(text = state.chainName, color = white64)
            if (state.isTestnet) B2(text = stringResource(R.string.label_testnet), color = white64)
            B2(text = stringResource(when (state.preference) {
                "shown" -> R.string.portfolio_filter_shown
                "hidden" -> R.string.portfolio_filter_hidden
                else -> R.string.portfolio_preference_auto
            }), color = white64)
            B1(text = state.amount)
            state.fiatAmount?.let { B2(text = it, color = white64) }
            state.hiddenNote?.let { B2(text = it, color = alertYellow,
                modifier = Modifier.testTag("ManageAssetItem_hidden_note_${state.id}")) }
        }
        ManageAssetsSwitch(state.isChecked, { onChecked(state, it) },
            Modifier.testTag("manage_toggle:${state.canonicalAssetKey}"))
    }
}

@Composable
private fun ManageAssetsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
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
    val trackColor = when {
        checked -> colorAccentDark
        else -> black3
    }
    Switch(
        colors = switchColors,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .background(color = trackColor, shape = RoundedCornerShape(20.dp))
            .padding(3.dp)
            .height(20.dp)
            .width(36.dp)
    )
}

@Composable
private fun GroupItem(
    groupAssets: List<ManageAssetItemState>,
    isCollapsed: Boolean,
    isNetwork: Boolean,
    onToggle: () -> Unit,
    onChecked: (Boolean) -> Unit
) {
    val all = groupAssets.all { it.isChecked }
    val none = groupAssets.none { it.isChecked }
    val toggleState = when {
        all -> androidx.compose.ui.state.ToggleableState.On
        none -> androidx.compose.ui.state.ToggleableState.Off
        else -> androidx.compose.ui.state.ToggleableState.Indeterminate
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = CenterVertically) {
        Column(Modifier.weight(1f).clickableWithNoIndication(onToggle)) {
            H3(text = if (isNetwork) groupAssets.first().chainName else groupAssets.first().symbol)
            B2(text = stringResource(R.string.portfolio_manage_group_count, groupAssets.count { it.isChecked }, groupAssets.size), color = white64)
            B2(text = stringResource(if (isCollapsed) R.string.common_expand else R.string.common_collapse), color = white64)
        }
        androidx.compose.material.TriStateCheckbox(state = toggleState, onClick = { onChecked(!all) },
            colors = androidx.compose.material.CheckboxDefaults.colors(checkedColor = colorAccentDark,
                uncheckedColor = white64, checkmarkColor = white))
    }
}

@Preview
@Composable
private fun ManageAssetsScreenPreview() {
    val items = listOf(
        ManageAssetItemState(
            canonicalAssetKey = "substrate:kusama:1",
            id = "1",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Moonriver.svg",
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
            canonicalAssetKey = "ethereum:moonriver:2",
            id = "2",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Kusama.svg",
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
            canonicalAssetKey = "substrate:westend:3",
            id = "3",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Kusama.svg",
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
            canonicalAssetKey = "substrate:two-tee:4",
            id = "4",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Kusama.svg",
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
                override fun onShowTestnetsChanged(show: Boolean) {}
            }
        )
    }
}
