package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import com.valentinilk.shimmer.defaultShimmerTheme

/** DstIn masks the skeleton with a quiet 8% band above its 60% base. */
val FwLoadingShimmerTheme = defaultShimmerTheme.copy(
    animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
    shaderColors = listOf(0.60f, 0.68f, 0.60f).map { FwStandard.textOnAccent.copy(alpha = it) }
)

const val FW_LOADING_SKELETON_ALPHA = 0.60f
