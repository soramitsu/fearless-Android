package jp.co.soramitsu.common.data.network.endpoint

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Bounded single-endpoint observation. Call only after a separately authorized explicit Verify action. */
class EndpointGenesisProbe internal constructor(private val runtime: EndpointProbeRuntime) {
    /** Creates an isolated verifier using the process-wide two-worker admission budget. */
    constructor() : this(EndpointProbeRuntime.shared)

    /** Makes one genesis request. Cancellation propagates; results confer no commit or selection authority. */
    suspend fun probe(request: EndpointProbeRequest): EndpointProbeOutcome {
        currentCoroutineContext().ensureActive()
        return withTimeoutOrNull(runtime.deadlineMillis) {
            suspendCancellableCoroutine { continuation ->
                val lease = runtime.acquire()
                if (lease == null) continuation.resume(EndpointProbeOutcome.BUSY)
                else {
                    continuation.invokeOnCancellation { lease.abort() }
                    runtime.execute(request, lease) { continuation.resume(it) }
                }
            }
        } ?: EndpointProbeOutcome.TIMED_OUT
    }
}
