package jp.co.soramitsu.common.compose.theme.fw

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.utils.onSingleClick
import jp.co.soramitsu.common.utils.rememberLastClickTime
import kotlinx.coroutines.delay

/** Press feedback: the control sinks (scale 0.97) in 90 ms and springs back in 160 ms. */
fun Modifier.fwPressScale(pressed: Boolean): Modifier = composed {
    val motion = fwMotion
    val target = if (pressed && motion.moves(FwMotionKind.Press)) FwLight.PRESS_SCALE else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = when {
            !motion.moves(FwMotionKind.Press) -> snap()
            pressed -> tween(FwMotionDurations.PRESS_IN)
            else -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "fwPress"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Emissive primary action: brand gradient with its own glow; it sinks while pressed. */
@Composable
fun FwPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes iconRes: Int? = null,
    textStyle: TextStyle = MaterialTheme.customTypography.header4
) {
    FwButton(text, onClick, modifier, enabled, loading, iconRes, textStyle, primary = true)
}

/** Secondary action: a raised key-lit surface that sinks into a well while pressed. */
@Composable
fun FwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes iconRes: Int? = null,
    textStyle: TextStyle = MaterialTheme.customTypography.header4,
    contentColor: Color = Color.Unspecified,
    tintIcon: Boolean = true
) {
    FwButton(text, onClick, modifier, enabled, loading, iconRes, textStyle, primary = false, contentColor = contentColor, tintIcon = tintIcon)
}

@Composable
private fun FwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    @DrawableRes iconRes: Int?,
    textStyle: TextStyle,
    primary: Boolean,
    contentColor: Color = Color.Unspecified,
    tintIcon: Boolean = true
) {
    val fx = LocalFwEffects.current
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val lastClick = rememberLastClickTime()
    val active = enabled && !loading
    val level = if (primary && enabled) FwElevation.Emissive else FwElevation.Raised1
    val labelColor = when {
        !enabled -> c.textTertiary
        primary -> c.textOnAccent
        contentColor != Color.Unspecified -> contentColor
        else -> c.textPrimary
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .fwPressScale(pressed && active)
            .fwSurface(fx, c, FwShape.Button, level, pressed = pressed && active, sunRim = 0f, interactive = true)
            .clip(FwShape.Button)
            .clickable(interactionSource = interactions, indication = null, enabled = active, role = Role.Button) {
                onSingleClick(lastClick, onClick)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                if (fwMotion.mode(FwMotionKind.SyncSweep) == FwMotionMode.Full) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = labelColor)
                } else {
                    Canvas(Modifier.size(16.dp).progressSemantics()) {
                        drawArc(labelColor, -90f, 270f, false, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            iconRes?.let {
                Icon(painter = painterResource(it), contentDescription = null, tint = if (tintIcon) labelColor else Color.Unspecified,
                    modifier = Modifier.size(if (tintIcon) 18.dp else 24.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text = text, style = textStyle, color = labelColor, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Round action (Send, Receive, Buy, Swap): a Raised-2 disc (Emissive when [primary]) with its label
 * below. The label is the accessible name.
 */
@Composable
fun FwRoundAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 60.dp
) {
    val fx = LocalFwEffects.current
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val lastClick = rememberLastClickTime()
    Column(
        modifier = modifier
            .widthIn(min = size + 8.dp)
            .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button) {
                onSingleClick(lastClick, onClick)
            }
            .alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .fwPressScale(pressed && enabled)
                .fwSurface(fx, c, CircleShape, if (primary) FwElevation.Emissive else FwElevation.Raised2,
                    pressed = pressed && enabled, interactive = true),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (primary) c.textOnAccent else c.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.customTypography.body2.copy(fontWeight = FontWeight.SemiBold),
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Raised-1 circular icon button for toolbars; 48 dp touch target, 40 dp disc. */
@Composable
fun FwIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    FwIconButton(painterResource(iconRes), contentDescription, onClick, modifier,
        if (tint == Color.Unspecified) LocalFwColors.current.textSecondary else tint)
}

/** [FwIconButton] for any painter; [tint] Unspecified keeps the painter's own colours (wallet icons). */
@Composable
fun FwIconButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val fx = LocalFwEffects.current
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .fwPressScale(pressed)
                .fwSurface(fx, c, CircleShape, FwElevation.Raised1, pressed = pressed, interactive = true),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Raised card on the page. Rims nearer the sun catch more of its light. */
@Composable
fun FwCard(
    modifier: Modifier = Modifier,
    level: FwElevation = FwElevation.Raised2,
    shape: Shape = FwShape.Card,
    sunRim: Float? = null,
    content: @Composable () -> Unit
) {
    Box(modifier.fwSurface(LocalFwEffects.current, LocalFwColors.current, shape, level, sunRim = sunRim).clip(shape)) {
        content()
    }
}

/**
 * Segmented control: an inset track with a raised segment that slides to the selection
 * (260 ms spring, a crossfade under reduced motion). [stacked] lists the options vertically for
 * large text.
 */
@Composable
fun FwSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    testTags: List<String>? = null
) {
    val fx = LocalFwEffects.current
    val c = LocalFwColors.current
    val motion = fwMotion
    if (stacked) {
        Column(modifier.fillMaxWidth().fwSurface(fx, c, FwShape.Well, FwElevation.Inset).padding(4.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .then(if (selected) Modifier.fwSurface(fx, c, FwShape.Button, FwElevation.Raised1, sunRim = 0f) else Modifier)
                        .clip(FwShape.Button)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(index) }
                        .then(testTags?.getOrNull(index)?.let { Modifier.fwTestTag(it) } ?: Modifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SegmentLabel(label, selected, c)
                }
            }
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min = 48.dp).fwSurface(fx, c, FwShape.Pill, FwElevation.Inset).padding(4.dp)) {
        val count = labels.size.coerceAtLeast(1)
        val segment = maxWidth / count
        val offset by animateDpAsState(
            targetValue = segment * selectedIndex.coerceIn(0, count - 1),
            animationSpec = if (motion.moves(FwMotionKind.TabWell)) {
                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
            } else snap(),
            label = "fwSegment"
        )
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.offset(x = offset).width(segment).fillMaxHeight()
                    .fwSurface(fx, c, FwShape.Pill, FwElevation.Raised1, sunRim = 0f)
            )
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(FwShape.Pill)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(index) }
                        .then(testTags?.getOrNull(index)?.let { Modifier.fwTestTag(it) } ?: Modifier)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SegmentLabel(label, selected, c)
                }
            }
        }
    }
}

@Composable
private fun SegmentLabel(label: String, selected: Boolean, c: FwColors) {
    val color by animateColorAsState(if (selected) c.textPrimary else c.textSecondary,
        tween(fwMotion.durationMillis(FwMotionKind.TabWell).coerceAtMost(FwMotionDurations.REDUCED_CROSSFADE)), label = "fwSegmentLabel")
    Text(
        text = label,
        style = MaterialTheme.customTypography.body1.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        color = color,
        textAlign = TextAlign.Center
    )
}

private fun Modifier.fwTestTag(tag: String): Modifier = testTag(tag)

/** Switch: an inset track; the thumb is raised when off and glows in brand light when on. */
@Composable
fun FwSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FwSwitchIndicator(
        checked = checked,
        modifier = modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.5f)
    )
}

/** The switch's paint alone, for rows that already carry the toggle semantics. */
@Composable
fun FwSwitchIndicator(checked: Boolean, modifier: Modifier = Modifier) {
    val fx = LocalFwEffects.current
    val c = LocalFwColors.current
    val motion = fwMotion
    val position by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        if (motion.moves(FwMotionKind.TabWell)) spring(dampingRatio = 0.75f) else snap(),
        label = "fwSwitch"
    )
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .fwSurface(fx, c, FwShape.Pill, FwElevation.Inset, interactive = true)
    ) {
        Box(
            Modifier.offset(x = position, y = 2.dp).size(28.dp)
                .fwSurface(
                    fx, c, CircleShape, if (checked) FwElevation.Emissive else FwElevation.Raised1, sunRim = 0f,
                    fill = if (checked) Color.Unspecified else c.thumbOff
                )
        )
    }
}

/**
 * Copy chip: tapping copies (through [onCopy]) and the chip glows success with "Copied" for 900 ms,
 * as a colour change only under reduced motion.
 */
@Composable
fun FwCopyChip(
    text: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int = R.drawable.ic_copy_16,
    maxLines: Int = 1
) {
    val fx = LocalFwEffects.current
    val c = LocalFwColors.current
    var copiedAt by remember { mutableStateOf(0L) }
    val copied = copiedAt != 0L
    LaunchedEffect(copiedAt) {
        if (copiedAt != 0L) {
            delay(FwMotionDurations.COPIED.toLong())
            copiedAt = 0L
        }
    }
    val chipShape = if (maxLines > 1) FwShape.Button else FwShape.Pill
    val tint by animateColorAsState(if (copied) c.success else c.textSecondary, tween(fwMotion.durationMillis(FwMotionKind.Copied).coerceAtMost(200)), label = "fwCopy")
    Box(
        modifier = modifier
            .then(if (copied && fx.glow) Modifier.fwGlow(fx, chipShape, color = c.success.copy(alpha = 0.35f), dy = 0.dp) else Modifier)
            .fwSurface(fx, c, chipShape, FwElevation.Raised1, interactive = true)
            .clip(chipShape)
            .clickable(role = Role.Button) {
                onCopy()
                copiedAt = System.nanoTime()
            }
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (copied) stringResource(R.string.common_copied) else text,
                style = MaterialTheme.customTypography.body1,
                color = if (copied) c.success else c.textPrimary,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (maxLines > 1) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

/** Empty state: an inset well holding the dimmed brand sun (or [iconRes]), a title and an optional hint. */
@Composable
fun FwEmptyState(title: String, modifier: Modifier = Modifier, description: String? = null, @DrawableRes iconRes: Int? = null) {
    val c = LocalFwColors.current
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).fwSurface(LocalFwEffects.current, c, CircleShape, FwElevation.Inset), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(iconRes ?: R.drawable.ic_fearless_logo),
                contentDescription = null,
                colorFilter = if (iconRes == null) ColorFilter.tint(c.textTertiary) else null,
                modifier = Modifier.size(if (iconRes == null) 52.dp else 36.dp)
            )
        }
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.customTypography.header5, color = c.textPrimary, textAlign = TextAlign.Center)
        description?.let {
            Spacer(Modifier.size(6.dp))
            Text(it, style = MaterialTheme.customTypography.body1.copy(fontSize = 13.sp), color = c.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Static sheet surface with a rounded, lit rim. Shared sheets also host signing and recovery
 * flows, so their decoration never relies on a caller remembering to opt out of animation.
 */
fun Modifier.fwSheet(): Modifier = composed {
    val c = LocalFwColors.current
    val fx = LocalFwEffects.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    background(c.surfaceRaised2, shape).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
        val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
        onDrawWithContent {
            drawContent()
            val edge = if (fx.highContrast) androidx.compose.ui.graphics.SolidColor(c.strokeControl)
            else rimBrush(c, FwLight.SUN_RIM_MAX * 0.4f, 120.dp.toPx())
            clipPath(path) { drawOutline(outline, edge, style = stroke) }
        }
    }
}

/** The page under a screen: the sun-lit light, or the legacy page image when FW_PALETTE=legacy. */
fun Modifier.fwPageBackground(): Modifier = composed {
    if (FwRestyle.paletteEnabled) {
        fwBackdrop(LocalFwEffects.current, LocalFwColors.current)
    } else {
        paint(painterResource(R.drawable.drawable_background_image), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
    }
}
