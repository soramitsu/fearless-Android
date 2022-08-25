package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector
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
fun Image(modifier: Modifier = Modifier, @DrawableRes res: Int, contentDescription: String? = null) {
    androidx.compose.foundation.Image(
        modifier = modifier,
        imageVector = ImageVector.vectorResource(
            id = res
        ),
        contentDescription = contentDescription
    )
}

fun Modifier.clickableWithNoIndication(onClick: () -> Unit): Modifier {
    return composed {
        clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    }
}
