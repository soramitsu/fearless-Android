package jp.co.soramitsu.onboarding.impl.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.theme.black
import jp.co.soramitsu.common.compose.theme.black50
import jp.co.soramitsu.common.compose.theme.green
import jp.co.soramitsu.common.compose.theme.white20
import jp.co.soramitsu.common.compose.theme.white60
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import kotlin.math.abs

@Composable
inline fun PagerIndicator(
    modifier: Modifier,
    gapSize: Dp,
    indicatorsSize: Int,
    currentPage: State<Int>,
    slideOffset: State<Float>,
    crossinline content: @Composable (itemIndex: Int) -> Unit
) {
    val elementsList = remember {
        Array(indicatorsSize) { Rect.Zero }
    }

    val layoutDirection = LocalLayoutDirection.current

    val color: Color by animateColorAsState(
        if (slideOffset.value != 0f) white20
        else white60,
        label = ""
    )

    Row(
        modifier = modifier
            .drawWithCache {
                val cornerRadius = CornerRadius(2000f, 2000f)
                val gapSizeHalf = gapSize.toPx() / 2

                onDrawWithContent {
                    val page = currentPage.value
                    val offset = slideOffset.value

                    if (elementsList.size == indicatorsSize) {
                        val element = elementsList[page]

                        val start = element.run {
                            if (offset < 0)
                                if (layoutDirection === LayoutDirection.Ltr)
                                    left - gapSizeHalf else left + gapSizeHalf
                            else left
                        }

                        val stop =
                            element.run {
                                /* for Arabic languages */
                                if (layoutDirection === LayoutDirection.Ltr)
                                    right + gapSizeHalf else left - gapSizeHalf
                            }

                        val sectorOffsetXLerp = lerp(
                            start = start,
                            stop = stop,
                            fraction = offset
                        )

                        val sectorOffsetY = 0f // Sliding only in horizontal plane

                        val sectorWidthLerp = lerp(
                            start = elementsList[page].width,
                            stop = (elementsList.getOrNull(page + 1)
                                ?: elementsList[page]).width,
                            fraction = abs(offset)
                        )

                        val sectorHeight = size.height

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(sectorOffsetXLerp, sectorOffsetY),
                            size = Size(sectorWidthLerp, sectorHeight),
                            cornerRadius = cornerRadius
                        )

                        drawContent()
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(gapSize),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(indicatorsSize) { index ->
            Box(
                modifier = Modifier.onPlaced {
                    elementsList[index] = it.boundsInParent()
                }
            ) {
                content.invoke(index)
            }
        }
    }
}

/**
 * lerp short for Linear Interpolation, google-known function
 */
fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

@Preview
@Composable
private fun PreviewSlidingPagerIndicator() {
    val ci = remember {
        mutableStateOf(0)
    }

    val d: List<Int> = ArrayDeque<Int>()

    val t = animateFloatAsState(
        targetValue = ci.value.toFloat(),
        label = "",
        animationSpec = tween(2000)
    )

    val selectedItemTextColor = green
    val notSelectedItemTextColor = black50

    Box(
        modifier = Modifier
            .clickable {
                ci.value = ci.value
                    .plus(1)
                    .rem(2)
            }
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        PagerIndicator(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            gapSize = 7.dp,
            indicatorsSize = 2,
            currentPage = ci,
            slideOffset = t,
        ) { index ->
            H1(
                modifier = Modifier
                    .size(70.dp)
                    .clickableWithNoIndication { ci.value = index }
                    .background(
                        if (index == ci.value) selectedItemTextColor else notSelectedItemTextColor,
                        CircleShape
                    ),
                text = "$index",
                color = black,
                textAlign = TextAlign.Center
            )
        }
    }
}