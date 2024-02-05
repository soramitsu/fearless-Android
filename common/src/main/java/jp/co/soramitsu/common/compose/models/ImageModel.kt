package jp.co.soramitsu.common.compose.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter

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

    class UrlWithFallbackOption(
        val url: String,
        val fallbackImageModel: ImageModel
    ): ImageModel

    class UrlWithPlaceholder(
        val url: String,
        val placeholderId: Int
    ): ImageModel

}

@Composable
fun ImageModel.retrievePainter(): Painter {
    return when(this) {

        is ImageModel.ResId -> painterResource(id = id)

        is ImageModel.Url -> rememberAsyncImagePainter(model = url)

        is ImageModel.UrlWithFallbackOption -> rememberAsyncImagePainter(
            model = url,
            error = fallbackImageModel.retrievePainter()
        )

        is ImageModel.UrlWithPlaceholder -> rememberAsyncImagePainter(
            model = url,
            placeholder = painterResource(id = placeholderId)
        )

    }
}