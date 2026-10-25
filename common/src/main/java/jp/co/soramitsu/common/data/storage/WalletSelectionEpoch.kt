package jp.co.soramitsu.common.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local selection revision. A→B→A invalidates prior work even if UI flows conflate B. */
object WalletSelectionEpoch {
    private var generation = 0L
    private var transitions = 0
    private val _changes = MutableStateFlow(0L)
    /** Invalidation signal; conflated A-to-B-to-A changes still have a different generation. */
    val changes = _changes.asStateFlow()

    /** Starts a central database selection transaction; readers fail closed until it finishes. */
    @Synchronized fun beginChange() { transitions++; generation++; _changes.value = generation }

    /** Invalidates snapshots on both successful and failed selection attempts. */
    @Synchronized fun endChange() { check(transitions > 0); generation++; transitions--; _changes.value = generation }

    /** Returns a stable version, or rejects work while selection is changing. */
    @Synchronized fun snapshot(): Long { check(transitions == 0) { "Wallet selection is changing" }; return generation }

    /** A returning wallet with the same address cannot revive an earlier operation. */
    @Synchronized fun isCurrent(expected: Long): Boolean = transitions == 0 && generation == expected

    /** Orders a synchronous secret/sign/send primitive before or after selection invalidation. Never suspend here. */
    @Synchronized fun <T> runIfCurrent(expected: Long, operation: () -> T): T {
        check(isCurrent(expected)) { "Wallet selection changed" }
        return operation()
    }

    /** Atomically skips stale UI/publication work while preserving failures from admitted work. */
    @Synchronized fun <T : Any> runIfCurrentOrNull(expected: Long, operation: () -> T): T? {
        if (!isCurrent(expected)) return null
        return operation()
    }

}
