package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.accentButtonColors
import jp.co.soramitsu.common.compose.theme.accentDarkButtonColors
import jp.co.soramitsu.common.compose.theme.accentDarkDisabledButtonColors
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.purple
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.utils.onSingleClick
import jp.co.soramitsu.common.utils.rememberLastClickTime

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
fun AccentButton(modifier: Modifier = Modifier, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        text = text,
        enabled = enabled,
        colors = accentDarkButtonColors,
        modifier = modifier,
        textStyle = MaterialTheme.customTypography.header4,
        onClick = onClick
    )
}

@Composable
fun AccentButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    if (loading) {
        ShapeButton(
            modifier = modifier,
            backgroundColor = colorAccentDark,
            onClick = {}
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.dp,
                color = white
            )
            MarginHorizontal(margin = 4.dp)
            Text(
                text = text,
                style = MaterialTheme.customTypography.header4,
                textAlign = TextAlign.Center
            )
        }
    } else {
        TextButton(
            text = text,
            enabled = enabled,
            colors = accentDarkButtonColors,
            modifier = modifier,
            textStyle = MaterialTheme.customTypography.header4,
            onClick = onClick
        )
    }
}

@Composable
fun AccentDarkDisabledButton(state: ButtonViewState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(text = state.text, enabled = state.enabled, colors = accentDarkDisabledButtonColors, modifier = modifier, onClick = onClick)
}

@Composable
fun GrayButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        text = text,
        enabled = enabled,
        colors = customButtonColors(white08),
        modifier = modifier,
        textStyle = MaterialTheme.customTypography.header4,
        onClick = onClick
    )
}

@Composable
fun GoogleButton(
    modifier: Modifier = Modifier,
    text: String = stringResource(id = R.string.onboarding_continue_with_google),
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = white64,
    onClick: () -> Unit
) {
    BackgroundCorneredWithBorder(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        borderColor = borderColor,
        backgroundColor = backgroundColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    modifier = Modifier.padding(end = 8.dp),
                    res = R.drawable.ic_google_30
                )
                Text(
                    text = text,
                    style = MaterialTheme.customTypography.header4
                )
            }
        }
    }
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
    FearlessAppTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(Color.Black)
        ) {
            AccentButton(
                text = "Start staking",
                enabled = false,
                loading = true,
                modifier = Modifier
                    .width(300.dp)
                    .height(52.dp)
            ) {}
            MarginVertical(margin = 16.dp)
            AccentButton(
                text = "Start staking Start staking Start staking",
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp)
            ) {}
            MarginVertical(margin = 16.dp)
            AccentButton(
                text = "Start staking",
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
            MarginVertical(margin = 16.dp)
            GoogleButton(onClick = {})
            MarginVertical(margin = 16.dp)
            GoogleButton(
                text = "Google",
                backgroundColor = white08,
                borderColor = Color.Unspecified,
                onClick = {}
            )
            MarginVertical(margin = 16.dp)
            AccentDarkDisabledButton(
                state = ButtonViewState("Dark Button text"),
                onClick = {}
            )
        }
    }
}
