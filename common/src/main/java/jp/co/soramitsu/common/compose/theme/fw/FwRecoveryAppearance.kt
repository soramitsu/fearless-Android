package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.FearlessCorneredShape

/** Recovery uses opaque Calm presentation. Both compile-time rollback controls preserve legacy content. */
@Composable
fun FwRecoveryAppearance(content: @Composable () -> Unit) {
    if (!FwRestyle.isEnabled(FwSurface.Recovery)) {
        content()
        return
    }
    FwCalm {
        val c = LocalFwColors.current
        CompositionLocalProvider(LocalContentColor provides c.textPrimary) {
            MaterialTheme(
                colors = MaterialTheme.colors.copy(
                    primary = c.textLink, primaryVariant = c.textLink, secondary = c.textLink,
                    background = c.bgBase, surface = c.surfaceRaised, error = c.danger,
                    onPrimary = c.bgBase, onBackground = c.textPrimary, onSurface = c.textPrimary,
                    onError = c.bgBase, isLight = false
                ),
                typography = MaterialTheme.typography.copy(
                    h5 = fwTextStyle(FwTextRole.TitleL), h6 = fwTextStyle(FwTextRole.TitleM),
                    subtitle1 = fwTextStyle(FwTextRole.TitleS), body1 = fwTextStyle(FwTextRole.BodyL),
                    body2 = fwTextStyle(FwTextRole.BodyM), button = fwTextStyle(FwTextRole.LabelL),
                    caption = fwTextStyle(FwTextRole.MonoS)
                ),
                content = content
            )
        }
    }
}

/** An explicit secondary action; repeated recovery controls have no shadow or glow. */
@Composable
fun FwRecoveryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val shape = remember { FearlessCorneredShape(8.dp, 10.dp) }
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .fwRecoveryFocus(focused, c.focus, shape)
            .fwNeuRaised(LocalFwEffects.current, c.copy(surfaceRaised = c.surfaceRaised2), shape, pressed = pressed, elevated = false)
            // This control paints its own pressed/focus states. A Material overlay would
            // lighten the declared fill and reduce the inside rim below 3:1.
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, modifier = Modifier.fillMaxWidth(), style = fwTextStyle(FwTextRole.LabelL), color = c.textPrimary, textAlign = TextAlign.Center)
    }
}

/** Tertiary navigation retains an explicit keyboard focus boundary without a filled surface. */
@Composable
fun FwRecoveryTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val shape = remember { RoundedCornerShape(8.dp) }
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused }
            .fwRecoveryFocus(focused, c.focus, shape),
        shape = shape,
        interactionSource = interactions,
        colors = ButtonDefaults.textButtonColors(contentColor = c.textLink)
    ) {
        Text(text, style = fwTextStyle(FwTextRole.LabelL), color = c.textLink)
    }
}

// The 2dp ring is outside the control with a 2dp gap; drawing adds no semantics or hit-area changes.
internal fun Modifier.fwRecoveryFocus(focused: Boolean, color: Color, shape: Shape): Modifier = if (!focused) this else drawWithCache {
    val expansion = 3.dp.toPx()
    val outline = shape.createOutline(Size(size.width + expansion * 2, size.height + expansion * 2), layoutDirection, this)
    val stroke = Stroke(2.dp.toPx())
    onDrawWithContent {
        drawContent()
        translate(-expansion, -expansion) { drawOutline(outline, color, style = stroke) }
    }
}
