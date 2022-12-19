package jp.co.soramitsu.common.compose.component

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import jp.co.soramitsu.common.compose.theme.bold
import jp.co.soramitsu.common.compose.theme.customTypography

@Composable
fun B1(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body1,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun B1(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body1,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun B2(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body2,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun B2(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body2,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun B0(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body0,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun B3(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.body3,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H1(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header1,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H2(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header2,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H3(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header3,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H3(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header3,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H3Bold(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header3.bold(),
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H4(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header4,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H4(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header4,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H5(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header5,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H5Bold(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header5.bold(),
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H5(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header5,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H6(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header6,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun H6(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text,
        style = MaterialTheme.customTypography.header6,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun CapsTitle(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text.uppercase(),
        style = MaterialTheme.customTypography.capsTitle,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun CapsTitle2(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        textAlign = textAlign,
        text = text.uppercase(),
        style = MaterialTheme.customTypography.capsTitle2,
        modifier = modifier,
        color = color,
        overflow = overflow,
        maxLines = maxLines
    )
}
