package jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen

import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ExpandableLazyListNestedScrollConnection(
    private val listState: LazyListState,
    private val parentHeight: MutableState<Dp>,
    private val blockHeight: MutableState<Dp>,
    private val height: MutableState<Dp>,
    private val scope: CoroutineScope,
    private val localDensity: Float,
    private val scrolled: ((Float) -> Unit)? = null
) : NestedScrollConnection {

    val expandPercent
        get() = runCatching { (height.value - blockHeight.value) / (parentHeight.value - blockHeight.value) }.getOrNull() ?: 0f

    fun preScrollFromParentLayout(available: Offset) {
        scroll(available, false)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return scroll(available, true)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        return scroll(available, true)
    }

    private fun scroll(available: Offset, fromList: Boolean): Offset {
        val isListEmpty = listState.layoutInfo.totalItemsCount == 0
        val isListAtTheTop = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset == 0
        val isListScrolledToTheEnd = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == listState.layoutInfo.totalItemsCount - 1

        val canScrollList = isListAtTheTop || isListScrolledToTheEnd.not()
        val hasReachedTop = height.value - (available.y / localDensity).dp >= parentHeight.value
        val hasReachedBottom = height.value - (available.y / localDensity).dp < blockHeight.value

        val offset = when {
            isListEmpty -> Offset.Zero
            hasReachedTop || hasReachedBottom -> {
                Offset.Zero
            }
            fromList.not() || (isListAtTheTop || isListScrolledToTheEnd) -> {
                height.value = height.value - (available.y / localDensity).dp
                available
            }
            isListScrolledToTheEnd || isListAtTheTop.not() -> {
                Offset.Zero
            }
            canScrollList -> {
                height.value = height.value - (available.y / localDensity).dp
                available
            }
            else -> {
                Offset.Zero
            }
        }
        scrolled?.invoke(offset.y)
        return offset
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return super.onPreFling(available)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        scrollToAnchor()
        return super.onPostFling(consumed, available)
    }

    private fun scrollToAnchor() {
        val scrollTo = when {
            expandPercent < 0.5f -> {
                blockHeight
            }
            expandPercent >= 0.5f -> {
                parentHeight
            }
            else -> null
        }?.value?.value ?: height.value.value

        animateScrollTo(scrollTo)
    }

    fun toggle() {
        val scrollTo = when {
            expandPercent < 0.5f -> {
                parentHeight
            }
            expandPercent >= 0.5f -> {
                blockHeight
            }
            else -> null
        }?.value?.value ?: height.value.value
        animateScrollTo(scrollTo)
    }

    private fun animateScrollTo(to: Float) {
        scope.launch {
            animate(height.value.value, to) { value, _ ->
                height.value = value.dp
            }
        }
    }
}
