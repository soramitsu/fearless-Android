package jp.co.soramitsu.common.compose.component

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.accentButtonColors
import jp.co.soramitsu.common.compose.theme.accentDarkDisabledButtonColors
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.purple

private const val DISABLE_CLICK_TIME = 1000L

data class ButtonViewState(
    val text: String,
    val enabled: Boolean = true
)

@Composable
fun AccentButton(state: ButtonViewState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(text = state.text, enabled = state.enabled, colors = accentButtonColors, modifier = modifier, onClick = onClick)
}

@Composable
fun AccentButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(text = text, enabled = enabled, colors = accentButtonColors, modifier = modifier, onClick = onClick)
}

@Composable
fun AccentDarkDisabledButton(state: ButtonViewState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(text = state.text, enabled = state.enabled, colors = accentDarkDisabledButtonColors, modifier = modifier, onClick = onClick)
}

@Composable
fun GrayButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(text = text, enabled = enabled, colors = customButtonColors(grayButtonBackground), modifier = modifier, onClick = onClick)
}

@Composable
fun TransparentButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(text = text, enabled = enabled, colors = customButtonColors(Color.Unspecified, colorAccentDark), modifier = modifier, onClick = onClick)
}

@Composable
fun TextButton(
    text: String,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.customTypography.header3,
    colors: ButtonColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FearlessButton(text = text, enabled = enabled, textStyle = textStyle, colors = colors, modifier = modifier, onClick = onClick)
}

@Composable
fun TextButtonSmall(
    text: String,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.customTypography.capsTitle2,
    colors: ButtonColors,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        shape = FearlessCorneredShape(cornerRadius = 4.dp, cornerCutLength = 6.dp),
        colors = colors,
        enabled = enabled,
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = textStyle,
            color = colors.contentColor(enabled = enabled).value
        )
    }
}

@Composable
fun FearlessButton(
    text: String,
    enabled: Boolean,
    textStyle: TextStyle = MaterialTheme.customTypography.header3,
    colors: ButtonColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val lastClickTimeState = rememberLastClickTime()
    TextButton(
        modifier = modifier,
        onClick = {
            onSingleClick(
                lastClickTimeState = lastClickTimeState,
                onClick = onClick
            )
        },
        shape = FearlessCorneredShape(),
        colors = colors,
        enabled = enabled,
        contentPadding = PaddingValues(vertical = 0.dp, horizontal = ButtonDefaults.TextButtonContentPadding.calculateLeftPadding(LayoutDirection.Ltr))
    ) {
        Text(
            text = text,
            style = textStyle,
            color = colors.contentColor(enabled = enabled).value,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun rememberLastClickTime(): MutableState<Long> {
    return remember { mutableStateOf(0L) }
}

private fun onSingleClick(
    lastClickTimeState: MutableState<Long>,
    onClick: () -> Unit
) {
    if (SystemClock.elapsedRealtime() - lastClickTimeState.value > DISABLE_CLICK_TIME) {
        lastClickTimeState.value = SystemClock.elapsedRealtime()
        onClick()
    }
}

@Composable
fun ColoredTextButton(
    text: String,
    enabled: Boolean = true,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        shape = FearlessCorneredShape(cornerRadius = 4.dp, cornerCutLength = 6.dp),
        colors = customButtonColors(backgroundColor),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        CapsTitle(text = text)
    }
}

@Composable
fun ColoredButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        shape = FearlessCorneredShape(cornerRadius = 4.dp, cornerCutLength = 6.dp),
        colors = customButtonColors(backgroundColor),
        border = border,
        enabled = enabled,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun ShapeButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color,
    border: BorderStroke? = null,
    shape: Shape = FearlessCorneredShape(cornerRadius = 4.dp, cornerCutLength = 6.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        colors = customButtonColors(backgroundColor),
        border = border,
        enabled = enabled,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
@Preview
fun ButtonPreview() {
    FearlessTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(Color.Black)
        ) {
            AccentButton(
                "Start staking Start staking Start staking ",
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp)
            ) {}
            MarginVertical(margin = 16.dp)
            AccentButton(
                "Start staking",
                enabled = false,
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp)
            ) {}
            MarginVertical(margin = 16.dp)
            ColoredTextButton(
                "Watch",
                backgroundColor = purple,
                modifier = Modifier
            ) {}
            MarginVertical(margin = 16.dp)
            GrayButton(
                "Create pool",
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp)
            ) {}
            MarginVertical(margin = 16.dp)
            TextButtonSmall(
                modifier = Modifier.height(24.dp),
                text = stringResource(id = R.string.staking_redeem),
                colors = customButtonColors(colorAccent),
                onClick = {}
            )
            MarginVertical(margin = 16.dp)
            TransparentButton(
                modifier = Modifier.height(52.dp),
                text = stringResource(id = R.string.staking_redeem),
                onClick = {}
            )
        }
    }
}
