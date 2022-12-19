package jp.co.soramitsu.common.utils

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

fun Modifier.clickableWithNoIndication(onClick: () -> Unit) = composed {
    clickableSingle(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
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

private class MultipleEventsCutter {
    private val now: Long
        get() = System.currentTimeMillis()

    private var lastEventTimeMs: Long = 0

    fun processEvent(event: () -> Unit) {
        if (now - lastEventTimeMs >= 300L) {
            event.invoke()
        }
        lastEventTimeMs = now
    }
}

fun Modifier.clickableSingle(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "clickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
    }
) {
    val multipleEventsCutter = remember { MultipleEventsCutter() }
    Modifier.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onClick = { multipleEventsCutter.processEvent { onClick() } },
        role = role,
        indication = indication,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    )
}
