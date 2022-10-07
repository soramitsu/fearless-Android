package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ButtonColors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.accentButtonColors
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.purple

data class ButtonViewState(
    val text: String,
    val enabled: Boolean = true
)

@Composable
fun AccentButton(state: ButtonViewState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(state.text, state.enabled, accentButtonColors, modifier, onClick)
}

@Composable
fun AccentButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(text, enabled, accentButtonColors, modifier, onClick)
}

@Composable
fun TextButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(text, enabled, customButtonColors(grayButtonBackground), modifier, onClick)
}

@Composable
fun TextButton(
    text: String,
    enabled: Boolean = true,
    colors: ButtonColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FearlessButton(text = text, enabled = enabled, colors = colors, modifier = modifier, onClick = onClick)
}

@Composable
fun TextButtonSmall(
    text: String,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.customTypography.capsTitle2,
    colors: ButtonColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        shape = FearlessCorneredShape(cornerRadius = 4.dp, cornerCutLength = 6.dp),
        colors = colors,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp)
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
    TextButton(
        modifier = modifier,
        onClick = onClick,
        shape = FearlessCorneredShape(),
        colors = colors,
        enabled = enabled
    ) {
        Text(
            text = text,
            style = textStyle,
            color = colors.contentColor(enabled = enabled).value
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
        CapsTitle(text = text.uppercase())
    }
}

@Composable
@Preview
private fun ButtonPreview() {
    FearlessTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AccentButton(
                "Start staking",
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
            TextButton(
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
        }
    }
}
