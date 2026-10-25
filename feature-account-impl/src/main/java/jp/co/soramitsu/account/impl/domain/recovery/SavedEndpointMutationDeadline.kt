package jp.co.soramitsu.account.impl.domain.recovery

import android.os.CancellationSignal
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Both the queued coroutine and SQLite signal expire after three seconds. Structured cancellation
 * waits for an already running physical worker; it cannot impose a hard bound on SQLite teardown.
 */
internal suspend fun <T> withSavedEndpointMutationDeadline(read: suspend (CancellationSignal) -> T): T =
    withTimeout(3_000) {
    coroutineScope {
        val signal = CancellationSignal()
        val deadline = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                delay(3_000)
            } finally {
                signal.cancel()
            }
        }
        try {
            withContext(Dispatchers.IO) { read(signal) }
        } finally {
            deadline.cancel()
            signal.cancel()
        }
    }
}
