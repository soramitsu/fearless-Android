package jp.co.soramitsu.common.compose.theme.fw

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R

/**
 * A list row on the page: Raised-1, and it sinks while pressed. A selected row carries the brand tint
 * and rim. Callers add a check as well, because colour alone must not report the selection.
 */
@Composable
fun FwListRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val sunk = pressed && enabled && onClick != null
    val shape = FwShape.Button
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .fwPressScale(sunk)
            .fwSurface(
                LocalFwEffects.current, c, shape, FwElevation.Raised1, pressed = sunk, interactive = onClick != null,
                fill = if (selected && !sunk) c.rowSelected.compositeOver(c.surfaceRaised) else Color.Unspecified
            )
            .then(if (selected) Modifier.border(1.5.dp, c.selectEdge, shape) else Modifier)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactions, indication = null, enabled = enabled, role = role, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics { if (selected) this.selected = true }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}

/** A quiet heading above a group of rows. */
@Composable
fun FwSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = fwTextStyle(FwTextRole.LabelM),
        color = LocalFwColors.current.textSecondary
    )
}

/** The check of a selected row, in brand light. */
@Composable
fun FwCheckMark(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(
        painter = painterResource(R.drawable.ic_checkmark_white_24),
        contentDescription = contentDescription,
        tint = LocalFwColors.current.textBrand,
        modifier = modifier.size(22.dp)
    )
}

/** A small tag in a well, such as Auto, Hidden or Testnet. */
@Composable
fun FwTag(text: String, modifier: Modifier = Modifier) {
    val c = LocalFwColors.current
    Box(
        modifier = modifier
            .fwSurface(LocalFwEffects.current, c, FwShape.Pill, FwElevation.Inset)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = fwTextStyle(FwTextRole.LabelS), color = c.textSecondary)
    }
}

/** A round well pressed into the surface; chain, asset and status icons sit in it. */
@Composable
fun FwIconWell(modifier: Modifier = Modifier, size: Dp = 40.dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .size(size)
            .fwSurface(LocalFwEffects.current, LocalFwColors.current, CircleShape, FwElevation.Inset),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Filter chip: a raised pill; the chosen one sinks into a well and lights up in brand. */
@Composable
fun FwFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .fwPressScale(pressed)
            .fwSurface(
                LocalFwEffects.current, c, FwShape.Pill, if (selected) FwElevation.Inset else FwElevation.Raised1,
                pressed = pressed, interactive = true
            )
            .then(if (selected) Modifier.border(1.5.dp, c.selectEdge, FwShape.Pill) else Modifier)
            .clip(FwShape.Pill)
            .selectable(selected = selected, interactionSource = interactions, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = fwTextStyle(FwTextRole.LabelM),
            color = if (selected) c.textPrimary else c.textSecondary
        )
    }
}

/** A raised pill that opens a chooser, with the chevron that says so. */
@Composable
fun FwSelectorPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .fwPressScale(pressed)
            .fwSurface(LocalFwEffects.current, c, FwShape.Pill, FwElevation.Raised1, pressed = pressed, interactive = true)
            .clip(FwShape.Pill)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            style = fwTextStyle(FwTextRole.LabelM),
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down_white),
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** A plain text action of at least 48 dp, such as Done. The press only dims it. */
@Composable
fun FwTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(FwShape.Button)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            modifier = Modifier.alpha(if (pressed) 0.6f else 1f),
            style = fwTextStyle(FwTextRole.LabelL),
            color = LocalFwColors.current.textLink
        )
    }
}

/**
 * A chevron that reports whether a section is open. It turns as the section opens, and jumps under
 * reduced motion and on secure surfaces.
 */
@Composable
fun FwExpandChevron(expanded: Boolean, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(fwMotion.durationMillis(FwMotionKind.TabWell)),
        label = "fwChevron"
    )
    Icon(
        painter = painterResource(R.drawable.ic_chevron_down_white),
        contentDescription = contentDescription,
        tint = LocalFwColors.current.textSecondary,
        modifier = modifier.size(20.dp).rotate(turn)
    )
}

/**
 * Three-state check for a group of rows: an inset box when off, the brand's own light when on or partly on.
 * The 48 dp box carries the toggle semantics.
 */
@Composable
fun FwTriStateCheck(state: ToggleableState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(48.dp)
            .triStateToggleable(state = state, interactionSource = interactions, indication = null, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .fwPressScale(pressed)
                .fwSurface(
                    LocalFwEffects.current, c, shape,
                    if (state == ToggleableState.Off) FwElevation.Inset else FwElevation.Emissive,
                    pressed = pressed, sunRim = 0f, interactive = true
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                ToggleableState.On -> Icon(
                    painter = painterResource(R.drawable.ic_checkmark_white_24),
                    contentDescription = null,
                    tint = c.textOnAccent,
                    modifier = Modifier.size(18.dp)
                )
                ToggleableState.Indeterminate -> Box(
                    Modifier.size(width = 12.dp, height = 2.dp).background(c.textOnAccent, RoundedCornerShape(1.dp))
                )
                ToggleableState.Off -> Unit
            }
        }
    }
}

/** What a notice reports. Each tone keeps its glyph, so it does not depend on colour alone. */
enum class FwNoticeTone { Neutral, Warning, Danger }

/**
 * A still notice for paused, unavailable and attention states: a status glyph and plain words in a well
 * pressed into the surface, with an optional action below. Nothing here pulses or moves.
 */
@Composable
fun FwNotice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: FwNoticeTone = FwNoticeTone.Warning,
    @DrawableRes iconRes: Int = R.drawable.ic_alert_triangle_yellow_24,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    val c = LocalFwColors.current
    val accent = when (tone) {
        FwNoticeTone.Neutral -> c.textSecondary
        FwNoticeTone.Warning -> c.warning
        FwNoticeTone.Danger -> c.danger
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fwSurface(
                LocalFwEffects.current, c, FwShape.Well, FwElevation.Inset,
                fill = if (tone == FwNoticeTone.Neutral) c.surfaceSunken else accent.container().compositeOver(c.surfaceSunken)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            title?.let {
                Text(it, style = fwTextStyle(FwTextRole.TitleS), color = c.textPrimary, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
            }
            Text(message, style = fwTextStyle(FwTextRole.BodyM), color = if (title == null) c.textPrimary else c.textSecondary)
            action?.let {
                Spacer(Modifier.height(10.dp))
                it()
            }
        }
    }
}
