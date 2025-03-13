package jp.co.soramitsu.common.utils

class MultipleEventsCutter {
    private val now: Long
        get() = System.currentTimeMillis()

    private var lastEventTimeMs: Long = 0

    fun processEvent(event: () -> Unit) {
        if (now - lastEventTimeMs >= 600L) {
            event.invoke()
        }
        lastEventTimeMs = now
    }
}