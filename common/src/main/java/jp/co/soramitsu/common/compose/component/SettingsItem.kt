package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.utils.clickableSingle

sealed interface SettingsItemAction {
    data object Transition : SettingsItemAction

    data class TransitionWithIcon(@DrawableRes val trailingIconRes: Int) : SettingsItemAction
    data class Selector(val text: String) : SettingsItemAction

    data class Switch(val value: Boolean) : SettingsItemAction

}

@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    icon: Painter,
    text: String,
    action: SettingsItemAction = SettingsItemAction.Transition,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clickableSingle(
                indication = LocalIndication.current
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(24.dp),
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.customColors.colorAccent
        )
        B1(
            modifier = Modifier
                .weight(1f)
                .padding(
                    vertical = 14.dp,
                    horizontal = 12.dp
                ),
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        when (action) {
            is SettingsItemAction.Selector -> {
                B1(text = action.text, color = white)
                MarginHorizontal(margin = 16.dp)
                Icon(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp),
                    painter = painterResource(R.drawable.ic_arrow_right_24),
                    contentDescription = null,
                    tint = MaterialTheme.customColors.white
                )
            }

            is SettingsItemAction.Switch -> {
                FearlessSwitch(isChecked = action.value, onCheckedChange = {onClick()})
                MarginHorizontal(margin = 16.dp)
            }

            SettingsItemAction.Transition -> {
                Icon(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp),
                    painter = painterResource(R.drawable.ic_arrow_right_24),
                    contentDescription = null,
                    tint = MaterialTheme.customColors.white
                )
            }

            is SettingsItemAction.TransitionWithIcon -> {
                Image(
                    modifier = Modifier
                        .size(16.dp),
                    action.trailingIconRes
                )
                MarginHorizontal(margin = 8.dp)
                Icon(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp),
                    painter = painterResource(R.drawable.ic_arrow_right_24),
                    contentDescription = null,
                    tint = MaterialTheme.customColors.white
                )
            }
        }
    }
}

@Composable
fun FearlessSwitch(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val trackColor = when {
        isChecked -> colorAccentDark
        else -> black3
    }
    Switch(
        colors = switchColors,
        checked = isChecked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier
            .background(color = trackColor, shape = RoundedCornerShape(20.dp))
            .padding(3.dp)
            .height(20.dp)
            .width(36.dp)
    )
}

val switchColors = object : SwitchColors {
    @Composable
    override fun thumbColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(white)
    }

    @Composable
    override fun trackColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(transparent)
    }
}

@Preview
@Composable
fun SettingsItemPreview() {
    Column {
        SettingsItem(
            icon = painterResource(R.drawable.ic_settings_wallets),
            text = "Item"
        )
        SettingsItem(
            icon = painterResource(R.drawable.ic_settings_wallets),
            text = "Item",
            action = SettingsItemAction.Selector("Value")
        )
        SettingsItem(
            icon = painterResource(R.drawable.ic_settings_wallets),
            text = "Item",
            action = SettingsItemAction.Switch(true)
        )
        SettingsItem(
            icon = painterResource(R.drawable.ic_settings_wallets),
            text = "Item",
            action = SettingsItemAction.Switch(false)
        )
        SettingsItem(
            icon = painterResource(R.drawable.ic_settings_wallets),
            text = "Item",
            action = SettingsItemAction.TransitionWithIcon(R.drawable.ic_warning_filled)
        )

    }
}
