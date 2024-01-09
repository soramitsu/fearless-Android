package jp.co.soramitsu.common.utils

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

private const val DisableClickTime = 1000L

@Composable
fun rememberLastClickTime(): MutableState<Long> {
    return remember { mutableStateOf(0L) }
}

fun onSingleClick(
    lastClickTimeState: MutableState<Long>,
    onClick: () -> Unit
) {
    if (SystemClock.elapsedRealtime() - lastClickTimeState.value > DisableClickTime) {
        lastClickTimeState.value = SystemClock.elapsedRealtime()
        onClick()
    }
}
