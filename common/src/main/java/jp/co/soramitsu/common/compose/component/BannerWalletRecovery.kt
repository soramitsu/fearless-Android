package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.container
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import jp.co.soramitsu.common.compose.theme.fw.fwRecoveryFocus
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * One key of the selected wallet needs attention; the other keys keep
 * working (addendum U0.2). Tapping it opens guided key recovery.
 */
@Composable
fun BannerWalletRecovery(viewOnly: Boolean = false, onClick: () -> Unit) {
    if (FwRestyle.isEnabled(FwSurface.Recovery)) {
        FwRecoveryAppearance { RecoveryAttentionBanner(viewOnly, onClick) }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .border(
                border = BorderStroke(1.dp, colorAccent),
                shape = RoundedCornerShape(15.dp)
            )
            .background(white04, RoundedCornerShape(15.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text(
            text = stringResource(if (viewOnly) R.string.pin_recovery_view_only_title else R.string.wallet_recovery_scoped_title),
            style = MaterialTheme.customTypography.headline2,
            color = Color.White
        )
        MarginVertical(margin = 8.dp)
        Text(
            text = stringResource(if (viewOnly) R.string.pin_recovery_view_only_intro else R.string.wallet_recovery_required_message),
            style = MaterialTheme.customTypography.paragraphXS,
            color = Color.White
        )
        MarginVertical(margin = 8.dp)
        Text(
            text = stringResource(if (viewOnly) R.string.pin_recovery_reset else R.string.wallet_recovery_fix_now),
            style = MaterialTheme.customTypography.paragraphSBold,
            color = colorAccent
        )
    }
}


/** Opaque status card: a warning glyph and leading bar accompany the scoped text. */
@Composable
private fun RecoveryAttentionBanner(viewOnly: Boolean, onClick: () -> Unit) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val shape = remember { RoundedCornerShape(12.dp) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .fwRecoveryFocus(focused, c.focus, shape)
            .clip(shape)
            .background(c.warning.container().compositeOver(c.bgBase))
            .drawBehind { drawRect(c.warning, size = Size(4.dp.toPx(), size.height)) }
            .clickable(interactionSource = interactions, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(painterResource(R.drawable.ic_alert_triangle_yellow_24), contentDescription = null,
            tint = c.warning, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (viewOnly) R.string.pin_recovery_view_only_title else R.string.wallet_recovery_scoped_title),
                style = fwTextStyle(FwTextRole.TitleS), color = c.textPrimary)
            Text(stringResource(if (viewOnly) R.string.pin_recovery_view_only_intro else R.string.wallet_recovery_required_message),
                style = fwTextStyle(FwTextRole.BodyM), color = c.textSecondary)
            Text(stringResource(if (viewOnly) R.string.pin_recovery_reset else R.string.wallet_recovery_fix_now),
                style = fwTextStyle(FwTextRole.LabelL), color = c.textLink)
        }
    }
}
