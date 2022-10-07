package jp.co.soramitsu.common.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

fun Modifier.clickableWithNoIndication(onClick: () -> Unit) = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

fun Modifier.toggleableWithNoIndication(value: Boolean, role: Role? = null, onValueChange: (Boolean) -> Unit) = composed {
    toggleable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onValueChange = onValueChange,
        value = value,
        role = role
    )
}
