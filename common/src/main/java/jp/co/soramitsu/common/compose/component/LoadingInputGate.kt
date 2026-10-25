package jp.co.soramitsu.common.compose.component

/** Main-thread state retaining busy transitions for pointer gestures that are already in flight. */
internal class LoadingInputGate {
    var revision: Long = 0
        private set
    private var loading = false

    fun update(isLoading: Boolean) {
        if (isLoading && !loading) revision++
        loading = isLoading
    }

    fun blocks(gestureRevision: Long): Boolean = loading || gestureRevision != revision
}
