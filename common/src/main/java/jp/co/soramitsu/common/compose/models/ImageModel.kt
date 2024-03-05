package jp.co.soramitsu.common.compose.models

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.size.Size
import coil.size.ViewSizeResolver
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.utils.dp
import okio.Path.Companion.toPath

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

        is ImageModel.Url -> {
            val context =
                LocalContext.current

            val imageLoader =
                remember {
                    ImageLoader.Builder(context)
                        .diskCache {
                            DiskCache.Builder()
                                .directory("${context.cacheDir}/nfts".toPath())
                                .maxSizePercent(.2)
                                .build()
                        }.build()
                }

            rememberAsyncImagePainter(
                model = url,
                imageLoader = imageLoader
            )
        }

        is ImageModel.Gif -> {
            val context =
                LocalContext.current

            val imageLoader =
                remember(id) {
                    ImageLoader.Builder(context)
                        .components {
                            if (Build.VERSION.SDK_INT >= 28) {
                                add(ImageDecoderDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                        }.build()
                }

            rememberAsyncImagePainter(
                model = id,
                imageLoader = imageLoader
            )
        }

        is ImageModel.UrlWithFallbackOption -> {
            val context =
                LocalContext.current

            val imageLoader =
                remember {
                    ImageLoader.Builder(context)
                        .diskCache {
                            DiskCache.Builder()
                                .directory("${context.cacheDir}/nfts".toPath())
                                .maxSizePercent(.2)
                                .build()
                        }.build()
                }

            rememberAsyncImagePainter(
                model = url,
                error = fallbackImageModel.retrievePainter(),
                imageLoader = imageLoader
            )
        }

        is ImageModel.UrlWithPlaceholder -> rememberAsyncImagePainter(
            model = url,
            placeholder = placeholderImageModel.retrievePainter()
        )

    }
}