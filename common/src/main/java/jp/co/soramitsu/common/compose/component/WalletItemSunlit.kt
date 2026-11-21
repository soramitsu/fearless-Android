package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.fw.FwCheckMark
import jp.co.soramitsu.common.compose.theme.fw.FwElevation
import jp.co.soramitsu.common.compose.theme.fw.FwIconButton
import jp.co.soramitsu.common.compose.theme.fw.FwShape
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.fwPressScale
import jp.co.soramitsu.common.compose.theme.fw.fwSurface
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import jp.co.soramitsu.common.compose.theme.white

/**
 * The wallet card in the sun-lit design. Unlike the page's other cards it reports its state: pressing
 * sinks it, and the chosen wallet carries the brand tint, the rim and a check.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SunlitWalletItem(
    state: WalletItemViewState,
    onOptionsClick: ((WalletItemViewState) -> Unit)?,
    onSelected: (WalletItemViewState) -> Unit,
    onLongClick: (WalletItemViewState) -> Unit,
    onScoreClick: (WalletItemViewState) -> Unit,
    modifier: Modifier
) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val shape = FwShape.Card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .fwPressScale(pressed)
            .fwSurface(
                LocalFwEffects.current, c, shape, FwElevation.Raised2, pressed = pressed, interactive = true,
                fill = if (state.isSelected && !pressed) c.rowSelected.compositeOver(c.surfaceRaised) else Color.Unspecified
            )
            .then(if (state.isSelected) Modifier.border(1.5.dp, c.selectEdge, shape) else Modifier)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onLongClick = { onLongClick(state) },
                onClick = { onSelected(state) }
            )
            .semantics { if (state.isSelected) selected = true }
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.CenterStart) {
            Icon(
                modifier = Modifier.fillMaxSize(),
                painter = rememberAsyncImagePainter(model = state.walletIcon),
                contentDescription = null,
                tint = Color.Unspecified
            )
        }
        Column(Modifier.weight(1f)) {
            Text(state.title, style = fwTextStyle(FwTextRole.BodyS), color = c.textSecondary)
            when {
                state.balance != null -> Text(
                    state.assetSymbol.orEmpty() + state.balance,
                    style = fwTextStyle(FwTextRole.TitleS),
                    color = c.textPrimary
                )
                state.balanceCaption == null -> {
                    MarginVertical(margin = 2.dp)
                    ShimmerB0(modifier = Modifier.width(60.dp))
                }
                else -> Text("—", style = fwTextStyle(FwTextRole.TitleS), color = c.textPrimary)
            }
            state.balanceCaption?.let { caption ->
                Text(
                    caption,
                    style = fwTextStyle(FwTextRole.BodyS),
                    color = c.textSecondary,
                    modifier = Modifier.testTag("WalletItem_balance_caption")
                )
            }
            if (state.changeBalanceViewState != null) {
                ChangeBalance(state = state.changeBalanceViewState)
            } else if (state.balance == null && state.balanceCaption == null) {
                MarginVertical(margin = 2.dp)
                ShimmerB2(modifier = Modifier.width(100.dp))
            }
        }
        if (state.isSelected) FwCheckMark()
        state.score?.let { score ->
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { onScoreClick(state) },
                contentAlignment = Alignment.Center
            ) {
                ScoreStar(score = score)
            }
        }
        onOptionsClick?.let { optionsAction ->
            FwIconButton(
                iconRes = R.drawable.ic_dots_horizontal_24,
                contentDescription = stringResource(R.string.ux_more),
                onClick = { optionsAction(state) },
                tint = white
            )
        }
    }
}

/**
 * The wallet row of a signing or approval sheet. The chosen wallet must be plain to see, so it carries the
 * brand rim and a check; nothing here animates beyond the press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SunlitWalletNameItem(
    state: WalletNameItemViewState,
    onOptionsClick: ((WalletNameItemViewState) -> Unit)?,
    onSelected: (WalletNameItemViewState) -> Unit,
    onLongClick: (WalletNameItemViewState) -> Unit,
    modifier: Modifier
) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val shape = FwShape.Card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .fwPressScale(pressed)
            .fwSurface(
                LocalFwEffects.current, c, shape, FwElevation.Raised1, pressed = pressed, interactive = true,
                fill = if (state.isSelected && !pressed) c.rowSelected.compositeOver(c.surfaceRaised) else Color.Unspecified
            )
            .then(if (state.isSelected) Modifier.border(1.5.dp, c.selectEdge, shape) else Modifier)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onLongClick = { onLongClick(state) },
                onClick = { onSelected(state) }
            )
            .semantics { if (state.isSelected) selected = true }
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(44.dp),
            painter = rememberAsyncImagePainter(model = state.walletIcon),
            contentDescription = null,
            tint = Color.Unspecified
        )
        Column(Modifier.weight(1f)) {
            Text(state.title, style = fwTextStyle(FwTextRole.LabelL), color = c.textPrimary)
            state.subtitle?.let { Text(it, style = fwTextStyle(FwTextRole.BodyS), color = c.textSecondary) }
        }
        if (state.isSelected) FwCheckMark()
        onOptionsClick?.let { optionsAction ->
            FwIconButton(
                iconRes = R.drawable.ic_dots_horizontal_24,
                contentDescription = stringResource(R.string.ux_more),
                onClick = { optionsAction(state) },
                tint = white
            )
        }
    }
}

/** The light wallet chooser: same rows, with the toolbar's raised back disc. */
@Composable
internal fun SunlitWalletSelectorScreen(
    state: WalletSelectorViewState,
    onWalletSelected: (WalletItemViewState) -> Unit,
    onBackClicked: () -> Unit
) {
    val c = LocalFwColors.current
    BottomSheetScreen {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                FwIconButton(
                    iconRes = R.drawable.ic_arrow_back_24dp,
                    contentDescription = stringResource(R.string.ux_back),
                    onClick = onBackClicked,
                    modifier = Modifier.align(Alignment.CenterStart),
                    tint = c.textPrimary
                )
                Text(
                    stringResource(R.string.common_title_select_wallet),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                    style = fwTextStyle(FwTextRole.TitleM),
                    color = c.textPrimary
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.wallets.map { it.copy(isSelected = it.id == state.selectedWallet?.id) }) { wallet ->
                    WalletItem(state = wallet, onSelected = onWalletSelected)
                }
            }
        }
    }
}
