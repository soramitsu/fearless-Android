package jp.co.soramitsu.common.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tells the launch clip that the app's first real screen is underneath it. The PIN page, the
 * welcome page and the wallet report themselves once they are on screen; nothing here can open,
 * skip or reorder any of them. It carries no wallet data.
 */
object LaunchLanding {
    private val shown = MutableStateFlow(false)

    /** True once a screen a person can act on has been shown in this process. */
    val ready: StateFlow<Boolean> = shown

    fun screenReady() {
        shown.value = true
    }

    /** One process plays the launch clip once: a cold start only. */
    @Volatile
    var clipConsumed: Boolean = false
        private set

    /** Returns true for the first caller in this process only. */
    @Synchronized
    fun consumeClip(): Boolean {
        if (clipConsumed) return false
        clipConsumed = true
        return true
    }

    /** For tests: a fresh process. */
    fun resetForTest() {
        shown.value = false
        clipConsumed = false
    }
}
