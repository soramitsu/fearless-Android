package jp.co.soramitsu.common.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

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

@OptIn(ExperimentalTextApi::class)
@Suppress("DEPRECATION")
fun String.withNoFontPadding(): AnnotatedString {
    val theText = this
    return buildAnnotatedString {
        withStyle(
            style = ParagraphStyle(
                platformStyle = PlatformParagraphStyle(false)
            )
        ) {
            append(text = theText)
        }
    }
}
