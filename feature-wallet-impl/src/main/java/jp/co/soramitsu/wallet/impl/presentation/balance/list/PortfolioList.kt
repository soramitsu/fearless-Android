package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.material.AlertDialog
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.*
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.common.EmptyAssetsContent
import jp.co.soramitsu.wallet.impl.presentation.common.PortfolioNetworkHeader
import jp.co.soramitsu.wallet.impl.presentation.common.SwipeableAssetListItem

/** Wallet-only rows; search and exact-asset screens keep their existing flat list. */
@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun PortfolioList(
    data: AssetsLoadingState.Loaded,
    isHideVisible: Boolean,
    callback: WalletScreenInterface,
    listState: LazyListState,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit
) {
    var action by remember(data.walletId) { mutableStateOf<Pair<String, ActionItemType>?>(null) }
    val walletId = data.walletId
    val members = action?.first?.let { group -> data.availableAssets.filter {
        !it.state.isTestnet && it.state.metadataTrust == "verified" &&
            data.tokenGroups?.groupOf(it.state.chainId, it.state.chainAssetId)?.id == group
    } }.orEmpty()
    fun act(group: PortfolioListItem.TokenGroup, type: ActionItemType) {
        val funded = data.availableAssets.filter { it.total?.signum() == 1 && it.state.isSupported &&
            !it.state.isTestnet && it.state.metadataTrust == "verified" &&
            data.tokenGroups?.groupOf(it.state.chainId, it.state.chainAssetId)?.id == group.id }
        if (type == ActionItemType.SEND && funded.size == 1 && walletId != null) {
            val member = funded.single().state
            callback.onPortfolioMemberAction(walletId, member.chainId, member.chainAssetId, type)
        } else action = group.id to type
    }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)) {
        item(key = "portfolio-header") { header() }
        if (data.assets.isEmpty() && data.portfolioItems.none { it == PortfolioListItem.EmptyWallet }) {
            item(key = "portfolio-empty") { EmptyAssetsContent(data.allAssetsHidden) }
        }
        items(data.portfolioItems, key = { it.key }) { item ->
            when (item) {
                is PortfolioListItem.AssetRow -> Column(Modifier.padding(start = if (item.nested) 12.dp else 0.dp)) {
                    if (data.portfolioUi.lens == PortfolioLens.ByToken || data.selectedNetwork != null) {
                        B2(text = item.asset.state.assetChainName, color = white64, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    }
                    if (item.asset.state.isDetected) jp.co.soramitsu.wallet.impl.presentation.common.DetectedAssetReviewItem(item.asset.state, callback)
                    else SwipeableAssetListItem(item.asset.state, isHideVisible, callback::assetClicked, callback::actionItemClicked)
                }
                is PortfolioListItem.NetworkHeader -> PortfolioNetworkHeader(item.first.state, !item.collapsed) {
                    callback.onPortfolioNetworkToggle(item.first.state.chainId)
                }
                is PortfolioListItem.TokenGroup -> PortfolioTokenGroup(item, { callback.onPortfolioGroupToggle(item.id) },
                    { act(item, ActionItemType.SEND) }, { act(item, ActionItemType.RECEIVE) },
                    { act(item, ActionItemType.HIDE) }, isHideVisible)
                is PortfolioListItem.ZeroSummary -> GrayButton(
                    text = stringResource(if (item.expanded) R.string.portfolio_hide_zero_networks else R.string.portfolio_show_zero_networks, item.networkCount),
                    modifier = Modifier.fillMaxWidth().testTag(item.key), onClick = { callback.onPortfolioZeroToggle(item.groupId) })
                PortfolioListItem.EmptyWallet -> EmptyAssetsContent(data.allAssetsHidden)
            }
        }
        item(key = "portfolio-footer") { footer() }
    }
    action?.let { selected ->
        val hide = selected.second == ActionItemType.HIDE
        AlertDialog(
            onDismissRequest = { action = null },
            backgroundColor = Color(0xFF191919), contentColor = Color.White,
            title = { H3(text = stringResource(if (hide) R.string.portfolio_hide_group_title else R.string.portfolio_choose_network)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (hide) B1(text = stringResource(R.string.portfolio_hide_group_message))
                    members.filter { !hide || !it.state.isHidden }.forEach { member ->
                        val row = member.state
                        Column(Modifier.fillMaxWidth().then(if (hide) Modifier else Modifier.clickable {
                            if (walletId != null) callback.onPortfolioMemberAction(walletId, row.chainId, row.chainAssetId, selected.second)
                            action = null
                        }).padding(vertical = 12.dp).semantics(mergeDescendants = true) {}) {
                            B1(text = row.assetChainName)
                            B2(text = member.total?.let { "${it.formatCrypto()} ${row.assetSymbol}" }
                                ?: stringResource(R.string.portfolio_balance_unavailable), color = white64)
                            row.networkAccountLabel?.let { B2(text = it, color = white64) }
                        }
                    }
                }
            },
            confirmButton = {
                if (hide) TextButton(onClick = {
                    if (walletId != null) callback.onPortfolioHideGroup(walletId, selected.first)
                    action = null
                }) { B1(text = stringResource(R.string.common_action_hide)) }
            },
            dismissButton = { TextButton(onClick = { action = null }) { B1(text = stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun PortfolioTokenGroup(
    group: PortfolioListItem.TokenGroup,
    toggle: () -> Unit,
    send: () -> Unit,
    receive: () -> Unit,
    hide: () -> Unit,
    canHide: Boolean
) {
    val sendLabel = stringResource(R.string.common_action_send)
    val receiveLabel = stringResource(R.string.common_action_receive)
    val hideLabel = stringResource(R.string.common_action_hide)
    val expansion = stringResource(if (group.expanded) R.string.common_collapse else R.string.common_expand)
    BackgroundCornered(modifier = Modifier.fillMaxWidth().testTag(group.key)) {
        Column(Modifier.padding(12.dp)) {
            Column(Modifier.fillMaxWidth().clickable(onClickLabel = expansion, onClick = toggle)
                .semantics(mergeDescendants = true) {
                    stateDescription = expansion
                    customActions = listOf(CustomAccessibilityAction(sendLabel) { send(); true },
                        CustomAccessibilityAction(receiveLabel) { receive(); true }) +
                        if (canHide) listOf(CustomAccessibilityAction(hideLabel) { hide(); true }) else emptyList()
                }) {
                AssetChainsBadge(group.members.flatMap { it.state.assetChainUrls.values }.distinct())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    H3(text = group.symbol, modifier = Modifier.weight(1f))
                    Icon(painterResource(if (group.expanded) R.drawable.ic_chevron_up_white else R.drawable.ic_chevron_down_white),
                        contentDescription = null, tint = Color.White)
                }
                B1(text = group.total?.let { "${it.formatCrypto()} ${group.symbol}" }
                    ?: stringResource(R.string.portfolio_balance_unavailable))
                B1(text = group.fiat?.formatFiat(group.members.first().fiatSymbol)?.let { if (group.priceIncomplete) "≈ $it" else it }
                    ?: stringResource(R.string.portfolio_price_unavailable))
                B2(text = stringResource(R.string.portfolio_group_networks, group.members.map { it.state.chainId }.distinct().size), color = white64)
                if (group.priceIncomplete) B2(text = stringResource(R.string.portfolio_price_incomplete), color = white64)
                if (group.hiddenCount > 0) B2(text = stringResource(R.string.portfolio_group_hidden, group.hiddenCount), color = white64)
            }
            if (LocalDensity.current.fontScale > 1.3f) {
                GrayButton(sendLabel, modifier = Modifier.fillMaxWidth(), onClick = send)
                GrayButton(receiveLabel, modifier = Modifier.fillMaxWidth(), onClick = receive)
                if (canHide) GrayButton(hideLabel, modifier = Modifier.fillMaxWidth(), onClick = hide)
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = send, modifier = Modifier.weight(1f)) { B1(text = sendLabel) }
                TextButton(onClick = receive, modifier = Modifier.weight(1f)) { B1(text = receiveLabel) }
                if (canHide) TextButton(onClick = hide, modifier = Modifier.weight(1f)) { B1(text = hideLabel) }
            }
        }
    }
}
