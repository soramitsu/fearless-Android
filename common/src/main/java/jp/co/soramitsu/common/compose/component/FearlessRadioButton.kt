package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.RadioButtonColors
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.accentRadioButtonColors
import jp.co.soramitsu.common.compose.theme.transparent

private val RadioButtonSize = 20.dp
private val RadioStrokeWidth = 1.dp

@Composable
fun FearlessRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: RadioButtonColors = accentRadioButtonColors
) {
    val radioColor = colors.radioColor(enabled, selected)
    val selectableModifier =
        if (onClick != null) {
            Modifier.selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null
            )
        } else {
            Modifier
        }

    Box(modifier = modifier.then(selectableModifier).padding(2.dp)) {
        if (selected) {
            Image(res = R.drawable.ic_selected, modifier = Modifier.size(RadioButtonSize), tint = radioColor.value)
        } else {
            Surface(
                modifier = Modifier
                    .size(RadioButtonSize)
                    .background(transparent, CircleShape)
                    .border(width = RadioStrokeWidth, color = radioColor.value, shape = CircleShape)
            ) {}
        }
    }
}

@Composable
@Preview
private fun Preview() {
    FearlessTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            val s = remember { mutableStateOf(true) }
            FearlessRadioButton(selected = s.value, onClick = { s.value = s.value.not() })
            FearlessRadioButton(selected = false, onClick = {})
        }
    }
}
