package jp.co.soramitsu.common.compose.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.compose.theme.shimmerColor

@Immutable
sealed interface Loadable<T> {

    @JvmInline
    value class ReadyToRender<T>(
        val data: T
    ): Loadable<T>

    class InProgress<T>(): Loadable<T>

}

@Composable
fun <T> Loadable<T>.Render(
    shimmerModifier: Modifier,
    shimmerRadius: Dp = 50.dp,
    content: @Composable (sharedModifier: Modifier, data: T) -> Unit
) {
    when(this) {

        is Loadable.ReadyToRender -> content.invoke(shimmerModifier, data)

        is Loadable.InProgress -> {
            Box(
                modifier = shimmerModifier.shimmer(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(shimmerColor, RoundedCornerShape(shimmerRadius))
                )
            }
        }

    }
}