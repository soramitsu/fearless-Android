package jp.co.soramitsu.common.compose.component

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import jp.co.soramitsu.common.compose.theme.customTypography

@Composable
fun B1(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body1,
        modifier = modifier,
        color = color
    )
}

@Composable
fun B2(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body2,
        modifier = modifier,
        color = color
    )
}

@Composable
fun B0(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body0,
        modifier = modifier,
        color = color
    )
}

@Composable
fun B3(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body3,
        modifier = modifier,
        color = color
    )
}

@Composable
fun H1(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header1,
        modifier = modifier,
        color = color
    )
}

@Composable
fun H2(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header2,
        modifier = modifier,
        color = color
    )
}

@Composable
fun H3(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header3,
        modifier = modifier,
        color = color
    )
}

@Composable
fun H4(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header4,
        modifier = modifier,
        color = color
    )
}

@Composable
fun H4(modifier: Modifier = Modifier, text: AnnotatedString, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header4,
        modifier = modifier,
        color = color
    )
}

@Composable
fun H5(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header5,
        modifier = modifier,
        color = color
    )
}

@Composable
fun H6(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header6,
        modifier = modifier,
        color = color
    )
}

@Composable
fun H6(modifier: Modifier = Modifier, text: AnnotatedString, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header6,
        modifier = modifier,
        color = color
    )
}

@Composable
fun CapsTitle(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.capsTitle,
        modifier = modifier,
        color = color
    )
}

@Composable
fun CapsTitle2(modifier: Modifier = Modifier, text: String, textAlign: TextAlign? = null, color: Color = Color.Unspecified) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.capsTitle2,
        modifier = modifier,
        color = color
    )
}
