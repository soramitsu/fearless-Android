package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.fw.FwCard
import jp.co.soramitsu.common.compose.theme.fw.FwCopyChip
import jp.co.soramitsu.common.compose.theme.fw.FwRollingText
import jp.co.soramitsu.common.compose.theme.fw.FwRoundAction
import jp.co.soramitsu.common.compose.theme.fw.FwShape
import jp.co.soramitsu.common.compose.theme.fw.FwSunBadge
import jp.co.soramitsu.common.compose.theme.fw.FwSyncPhase
import jp.co.soramitsu.common.compose.theme.fw.FwSyncState
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.fwHudLabel
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_wallet_impl.R

/** What the hero sun reports, from state the wallet already has: no new polling. */
internal fun walletSyncState(state: WalletState): FwSyncState = when {
    state.assetsState is WalletAssetsState.NetworkIssue -> FwSyncState(FwSyncPhase.Failed)
    state.assetsState is WalletAssetsState.Assets && state.assetsState.assets is AssetsLoadingState.Loading ->
        FwSyncState(FwSyncPhase.Running)
    state.hasNetworkIssues -> FwSyncState(FwSyncPhase.Failed)
    else -> FwSyncState(FwSyncPhase.Done)
}

/** Shortened address for the copy chip: first and last characters, the full value is copied. */
internal fun heroAddressLabel(address: String): String =
    if (address.length <= ADDRESS_SHORT_LIMIT) address else address.take(ADDRESS_EDGE) + "…" + address.takeLast(ADDRESS_EDGE)

private const val ADDRESS_SHORT_LIMIT = 16
private const val ADDRESS_EDGE = 6

/**
 * Sun-lit wallet hero: the approved badge as the emissive sun (its sync arc tracks the real asset
 * loading), the total that rolls to new values, today's change, the address chip, then the round
 * Send / Receive / Buy / Swap actions. Test tags of the legacy header are kept.
 */
@Composable
fun SunlitWalletHero(
    state: AssetBalanceViewState,
    sync: FwSyncState,
    onBalanceClick: () -> Unit,
    onAddressClick: () -> Unit,
    onAction: (ActionItemType) -> Unit
) {
    val c = LocalFwColors.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val compact = LocalDensity.current.fontScale > 1.3f
    val syncLine = when (sync.phase) {
        FwSyncPhase.Running -> stringResource(R.string.portfolio_balance_loading)
        FwSyncPhase.Failed -> stringResource(R.string.portfolio_sync_error)
        else -> null
    }
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        FwCard(modifier = Modifier.fillMaxWidth().testTag("wallet_hero"), shape = FwShape.Hero, sunRim = 0.45f) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FwSunBadge(
                    sync = sync,
                    badgeSize = if (compact) 72.dp else 96.dp,
                    contentDescription = when (sync.phase) {
                        FwSyncPhase.Running -> stringResource(R.string.portfolio_balance_loading)
                        FwSyncPhase.Failed -> stringResource(R.string.portfolio_sync_error)
                        else -> null
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = fwHudLabel(stringResource(R.string.common_total), language),
                    style = MaterialTheme.customTypography.body2.copy(letterSpacing = 0.06.em, fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 2.dp)
                        .testTag("balance_fiat")
                        .clickableWithNoIndication(onBalanceClick)
                ) {
                    FwRollingText(
                        text = state.transferableBalance ?: "—",
                        style = MaterialTheme.customTypography.header1.copy(
                            fontSize = if (compact) 30.sp else 38.sp,
                            letterSpacing = (-0.02).em,
                            fontFeatureSettings = "tnum"
                        ),
                        color = c.textPrimary
                    )
                    if (state.isInfoEnabled) {
                        Spacer(Modifier.width(6.dp))
                        Icon(painterResource(R.drawable.ic_info_white_24), contentDescription = null,
                            tint = c.textTertiary, modifier = Modifier.size(14.dp))
                    }
                }
                state.changeViewState?.takeIf { it.fiatChange.isNotEmpty() }?.let { change ->
                    val up = !change.percentChange.startsWith("-")
                    val tone = if (up) c.priceUp else c.priceDown
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = (if (up) "▲ " else "▼ ") + change.percentChange,
                            style = MaterialTheme.customTypography.body1.copy(fontWeight = FontWeight.SemiBold),
                            color = tone,
                            modifier = Modifier.testTag("balance_change_percent")
                        )
                        change.percentFiatChange?.let {
                            Text(it, style = MaterialTheme.customTypography.body1, color = tone,
                                modifier = Modifier.padding(start = 4.dp).testTag("fiat_balance_change_percent"))
                        }
                        Text(
                            text = "· ${change.fiatChange}",
                            style = MaterialTheme.customTypography.body1,
                            color = c.textSecondary,
                            modifier = Modifier.padding(start = 6.dp).testTag("balance_change_fiat")
                        )
                    }
                }
                state.balanceCaption?.takeUnless { it == syncLine }?.let {
                    Text(it, style = MaterialTheme.customTypography.body2, color = c.textTertiary,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp).testTag("balance_caption"))
                }
                syncLine?.let {
                    Text(it, style = MaterialTheme.customTypography.body2, color = if (sync.phase == FwSyncPhase.Failed) c.warning else c.textTertiary,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).testTag("wallet_sync_status"))
                }
                if (state.address.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FwCopyChip(
                        text = heroAddressLabel(state.address),
                        onCopy = onAddressClick,
                        modifier = Modifier.widthIn(max = 240.dp).testTag("balance_address")
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            val actions = listOf(
                Triple(ActionItemType.SEND, R.drawable.ic_common_send, true),
                Triple(ActionItemType.RECEIVE, R.drawable.ic_common_receive, false),
                Triple(ActionItemType.BUY, R.drawable.ic_common_buy, false),
                Triple(ActionItemType.SWAP, R.drawable.ic_exchange, false)
            )
            actions.forEach { (type, icon, primary) ->
                FwRoundAction(
                    iconRes = icon,
                    label = stringResource(type.titleId),
                    onClick = { onAction(type) },
                    primary = primary,
                    size = if (compact) 52.dp else 58.dp,
                    modifier = Modifier.weight(1f).testTag("wallet_hero_${type.name.lowercase()}")
                )
            }
        }
    }
}
