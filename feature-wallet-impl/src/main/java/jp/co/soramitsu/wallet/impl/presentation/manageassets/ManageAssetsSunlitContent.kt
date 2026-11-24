package jp.co.soramitsu.wallet.impl.presentation.manageassets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.fw.FwEmptyState
import jp.co.soramitsu.common.compose.theme.fw.FwExpandChevron
import jp.co.soramitsu.common.compose.theme.fw.FwIconWell
import jp.co.soramitsu.common.compose.theme.fw.FwListRow
import jp.co.soramitsu.common.compose.theme.fw.FwSectionLabel
import jp.co.soramitsu.common.compose.theme.fw.FwSelectorPill
import jp.co.soramitsu.common.compose.theme.fw.FwSwitchIndicator
import jp.co.soramitsu.common.compose.theme.fw.FwTag
import jp.co.soramitsu.common.compose.theme.fw.FwTextAction
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.FwTriStateCheck
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import jp.co.soramitsu.feature_wallet_impl.R

private val GUTTER = 20.dp

/**
 * Manage assets in the sun-lit design: raised rows with the asset's icon in a well, the lit switch, and
 * each group's rows set in under it. Same state, same callbacks and same test tags as the legacy page.
 * The list keeps the page gutter as content padding, so its rows' light is not cut at the list's edges.
 */
@Composable
internal fun ManageAssetsSunlitContent(
    state: ManageAssetsScreenViewState,
    callback: ManageAssetsContentInterface
) {
    Column(Modifier.nestedScroll(rememberNestedScrollInteropConnection()).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = GUTTER),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FwTextAction(stringResource(R.string.common_done), callback::onDoneClicked)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                FwSelectorPill(state.selectedChainTitle, callback::onSelectedChainClicked)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = GUTTER, end = GUTTER, top = 12.dp, bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "manage-controls") { SunlitControls(state, callback) }
            when {
                state.assets == null -> item {
                    Box(Modifier.fillMaxWidth().height(120.dp)) { FearlessProgress(Modifier.align(Alignment.Center)) }
                }
                state.assets.isEmpty() -> item {
                    if (state.visibilityFilter == ManageAssetFilter.Hidden) {
                        FwEmptyState(stringResource(R.string.portfolio_no_hidden))
                    } else {
                        FwEmptyState(
                            title = stringResource(R.string.common_search_assets_alert_title),
                            description = stringResource(R.string.common_empty_search)
                        )
                    }
                }
                else -> state.assets.forEach { (section, assets) ->
                    if (!section.startsWith("group:") && !section.startsWith("network:")) {
                        items(assets, key = ManageAssetItemState::listKey) { SunlitAssetRow(it, callback::onChecked) }
                    } else {
                        val collapsed = section in state.collapsedSections && state.searchQuery.isNullOrBlank()
                        item(key = "header:$section") {
                            SunlitGroupRow(
                                groupAssets = state.allSectionAssets[section] ?: assets,
                                isCollapsed = collapsed,
                                isNetwork = section.startsWith("network:"),
                                onToggle = { callback.onSectionToggle(section) },
                                onChecked = { callback.onGroupChecked(section, it) }
                            )
                        }
                        if (!collapsed) {
                            items(assets, key = ManageAssetItemState::listKey) {
                                SunlitAssetRow(it.copy(isGrouped = true), callback::onChecked)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SunlitControls(state: ManageAssetsScreenViewState, callback: ManageAssetsContentInterface) {
    val c = LocalFwColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CorneredInput(state = state.searchQuery, onInput = callback::onSearchInput, hintLabel = stringResource(R.string.common_search))
        MultiToggleButton(
            MultiToggleButtonState(state.visibilityFilter, ManageAssetFilter.entries),
            callback::onFilterChanged,
            stacked = LocalDensity.current.fontScale > 1.3f
        )
        FwListRow(modifier = Modifier.testTag("ManageAssets_show_testnets"), contentPadding = RowPadding) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.portfolio_show_testnets), style = fwTextStyle(FwTextRole.LabelL), color = c.textPrimary)
                Text(
                    stringResource(R.string.portfolio_show_testnets_description),
                    style = fwTextStyle(FwTextRole.BodyS),
                    color = c.textSecondary
                )
            }
            SunlitSwitch(state.showTestnets, callback::onShowTestnetsChanged)
        }
        Spacer(Modifier.height(2.dp))
        FwSectionLabel(stringResource(R.string.wallet_manage_assets))
    }
}

@Composable
private fun SunlitAssetRow(state: ManageAssetItemState, onChecked: (ManageAssetItemState, Boolean) -> Unit) {
    val c = LocalFwColors.current
    val preference = stringResource(
        when (state.preference) {
            "shown" -> R.string.portfolio_filter_shown
            "hidden" -> R.string.portfolio_filter_hidden
            else -> R.string.portfolio_preference_auto
        }
    )
    val testnet = if (state.isTestnet) stringResource(R.string.label_testnet) else null
    // At large text the amount goes under the name so that neither is squeezed.
    val stacked = LocalDensity.current.fontScale > 1.3f
    FwListRow(
        modifier = if (state.isGrouped) Modifier.padding(start = 16.dp) else Modifier,
        contentPadding = RowPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FwIconWell {
            AsyncImage(
                model = state.imageUrl?.let { getImageRequest(LocalContext.current, it) },
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.symbol, style = fwTextStyle(FwTextRole.LabelL), color = c.textPrimary)
                FwTag(preference)
                testnet?.let { FwTag(it) }
            }
            Text(state.chainName, style = fwTextStyle(FwTextRole.BodyS), color = c.textSecondary)
            state.hiddenNote?.let {
                Text(
                    it,
                    style = fwTextStyle(FwTextRole.BodyS),
                    color = c.warning,
                    modifier = Modifier.testTag("ManageAssetItem_hidden_note_${state.id}")
                )
            }
            if (stacked) SunlitAmount(state, Alignment.Start)
        }
        if (!stacked) Box(Modifier.widthIn(max = 112.dp)) { SunlitAmount(state, Alignment.End) }
        SunlitSwitch(
            checked = state.isChecked,
            onCheckedChange = { onChecked(state, it) },
            modifier = Modifier.testTag("manage_toggle:${state.canonicalAssetKey}")
        )
    }
}

@Composable
private fun SunlitAmount(state: ManageAssetItemState, alignment: Alignment.Horizontal) {
    val c = LocalFwColors.current
    val textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start
    Column(horizontalAlignment = alignment) {
        Text(state.amount, style = fwTextStyle(FwTextRole.NumM), color = c.textPrimary, textAlign = textAlign)
        state.fiatAmount?.let {
            Text(it, style = fwTextStyle(FwTextRole.NumS), color = c.textSecondary, textAlign = textAlign)
        }
    }
}

@Composable
private fun SunlitGroupRow(
    groupAssets: List<ManageAssetItemState>,
    isCollapsed: Boolean,
    isNetwork: Boolean,
    onToggle: () -> Unit,
    onChecked: (Boolean) -> Unit
) {
    val c = LocalFwColors.current
    val all = groupAssets.all { it.isChecked }
    val none = groupAssets.none { it.isChecked }
    val toggleState = when {
        all -> ToggleableState.On
        none -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
    FwListRow(
        onClick = onToggle,
        contentPadding = PaddingValues(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isNetwork) groupAssets.first().chainName else groupAssets.first().symbol,
                style = fwTextStyle(FwTextRole.TitleS),
                color = c.textPrimary
            )
            Text(
                stringResource(R.string.portfolio_manage_group_count, groupAssets.count { it.isChecked }, groupAssets.size),
                style = fwTextStyle(FwTextRole.BodyS),
                color = c.textSecondary
            )
        }
        FwExpandChevron(
            expanded = !isCollapsed,
            contentDescription = stringResource(if (isCollapsed) R.string.common_expand else R.string.common_collapse)
        )
        FwTriStateCheck(toggleState, onClick = { onChecked(!all) })
    }
}

/** The lit switch with a 48 dp target; the outer box carries the toggle semantics and the test tag. */
@Composable
private fun SunlitSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center
    ) {
        FwSwitchIndicator(checked)
    }
}

private val RowPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
