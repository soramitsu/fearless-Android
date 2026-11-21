package jp.co.soramitsu.account.impl.presentation.appearance

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.fw.FwCard
import jp.co.soramitsu.common.compose.theme.fw.FwElevation
import jp.co.soramitsu.common.compose.theme.fw.FwNotice
import jp.co.soramitsu.common.compose.theme.fw.FwNoticeTone
import jp.co.soramitsu.common.compose.theme.fw.FwPrimaryButton
import jp.co.soramitsu.common.compose.theme.fw.FwSecondaryButton
import jp.co.soramitsu.common.compose.theme.fw.FwSegmented
import jp.co.soramitsu.common.compose.theme.fw.FwSectionLabel
import jp.co.soramitsu.common.compose.theme.fw.FwSwitchIndicator
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.FwTierChoice
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.fwSurface
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import jp.co.soramitsu.feature_account_impl.R

/**
 * Settings > Appearance: how much light and depth the app paints. The theme applies each change at once,
 * so the page itself and its preview card show the result. Nothing here reaches wallet data.
 */
@Composable
fun AppearanceScreen(
    state: AppearanceState,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onReducedChanged: (Boolean) -> Unit,
    onContrastChanged: (Boolean) -> Unit,
    onRetry: () -> Unit
) {
    val c = LocalFwColors.current
    val effects = LocalFwEffects.current
    val reduced = state.appearance.choice == FwTierChoice.Reduced
    val keptReduced = !reduced && !state.appearance.highContrast && effects.tier == FwTier.Reduced
    Column(Modifier.fillMaxSize()) {
        Toolbar(
            modifier = Modifier.padding(vertical = 8.dp),
            state = ToolbarViewState(stringResource(R.string.appearance_title), jp.co.soramitsu.common.R.drawable.ic_arrow_back_24dp),
            onNavigationClick = onBack
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FwSectionLabel(stringResource(R.string.appearance_effects_title))
            FwSegmented(
                labels = listOf(
                    stringResource(R.string.appearance_effects_automatic),
                    stringResource(R.string.appearance_effects_reduced)
                ),
                selectedIndex = if (reduced) 1 else 0,
                onSelect = { onReducedChanged(it == 1) },
                stacked = LocalDensity.current.fontScale > 1.3f,
                testTags = listOf("appearance_effects_automatic", "appearance_effects_reduced")
            )
            Text(
                stringResource(if (reduced) R.string.appearance_effects_reduced_description else R.string.appearance_effects_automatic_description),
                style = fwTextStyle(FwTextRole.BodyM),
                color = c.textSecondary
            )
            if (keptReduced) {
                FwNotice(
                    message = stringResource(R.string.appearance_effects_kept_reduced),
                    tone = FwNoticeTone.Neutral,
                    modifier = Modifier.testTag("appearance_kept_reduced")
                )
            }
            ContrastRow(state.appearance.highContrast, onContrastChanged)
            PreviewCard()
            if (state.saveFailed) {
                FwNotice(
                    message = stringResource(R.string.appearance_save_failed),
                    tone = FwNoticeTone.Danger,
                    modifier = Modifier.testTag("appearance_save_failed"),
                    action = { FwSecondaryButton(stringResource(jp.co.soramitsu.common.R.string.common_retry), onRetry, Modifier.fillMaxWidth()) }
                )
            }
        }
    }
}

@Composable
private fun ContrastRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalFwColors.current
    FwCard(Modifier.fillMaxWidth(), level = FwElevation.Raised1, shape = jp.co.soramitsu.common.compose.theme.fw.FwShape.Button) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .testTag("appearance_contrast")
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.appearance_contrast_title), style = fwTextStyle(FwTextRole.LabelL), color = c.textPrimary)
                Text(stringResource(R.string.appearance_contrast_description), style = fwTextStyle(FwTextRole.BodyS), color = c.textSecondary)
            }
            FwSwitchIndicator(checked)
        }
    }
}

/** A still sample of the app's surfaces under the current effects. It is decoration: screen readers skip it. */
@Composable
private fun PreviewCard() {
    val c = LocalFwColors.current
    val effects = LocalFwEffects.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.appearance_preview_title),
            style = fwTextStyle(FwTextRole.LabelM),
            color = c.textSecondary,
            modifier = Modifier.semantics { heading() }
        )
        FwCard(Modifier.fillMaxWidth().testTag("appearance_preview").clearAndSetSemantics { }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).fwSurface(effects, c, CircleShape, FwElevation.Inset), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(jp.co.soramitsu.common.R.drawable.ic_fearless_logo),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("SORA", style = fwTextStyle(FwTextRole.LabelL), color = c.textPrimary)
                        Text("1,250.00", style = fwTextStyle(FwTextRole.NumS), color = c.textSecondary)
                    }
                    FwSwitchIndicator(true)
                }
                if (LocalDensity.current.fontScale > 1.3f) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FwSecondaryButton(stringResource(jp.co.soramitsu.common.R.string.common_cancel), {}, Modifier.fillMaxWidth())
                        FwPrimaryButton(stringResource(jp.co.soramitsu.common.R.string.common_continue), {}, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FwSecondaryButton(stringResource(jp.co.soramitsu.common.R.string.common_cancel), {}, Modifier.weight(1f))
                        FwPrimaryButton(stringResource(jp.co.soramitsu.common.R.string.common_continue), {}, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
