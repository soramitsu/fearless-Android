package jp.co.soramitsu.common.compose.models

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

@Immutable
sealed interface ImageModel {

    @JvmInline
    value class ResId(
        val id: Int
    ): ImageModel

    @JvmInline
    value class Url(
        val url: String
    ): ImageModel

    @JvmInline
    value class Gif(
        val id: Int
    ): ImageModel

    class UrlWithFallbackOption(
        val url: String,
        val fallbackImageModel: ImageModel
    ): ImageModel

    class UrlWithPlaceholder(
        val url: String,
        val placeholderImageModel: ImageModel
    ): ImageModel

}

@Composable
fun ImageModel.retrievePainter(): Painter {
    return when(this) {

        is ImageModel.ResId -> painterResource(id = id)

        is ImageModel.Url -> rememberAsyncImagePainter(model = url)

        is ImageModel.Gif -> rememberAsyncImagePainter(
            model = id,
            imageLoader = ImageLoader.Builder(LocalContext.current).components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }.build()
        )

        is ImageModel.UrlWithFallbackOption -> rememberAsyncImagePainter(
            model = url,
            error = fallbackImageModel.retrievePainter()
        )

        is ImageModel.UrlWithPlaceholder -> rememberAsyncImagePainter(
            model = url,
            placeholder = placeholderImageModel.retrievePainter()
        )

    }
}