package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import coil.decode.SvgDecoder
import coil.request.ImageRequest

fun getImageRequest(context: Context, url: String): ImageRequest {
    return ImageRequest.Builder(context)
        .data(url)
        .decoderFactory(SvgDecoder.Factory())
        .build()
}

@Composable
fun Image(
    modifier: Modifier = Modifier,
    @DrawableRes res: Int,
    tint: Color? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    androidx.compose.foundation.Image(
        modifier = modifier,
        imageVector = ImageVector.vectorResource(
            id = res
        ),
        contentDescription = contentDescription,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        contentScale = contentScale
    )
}
