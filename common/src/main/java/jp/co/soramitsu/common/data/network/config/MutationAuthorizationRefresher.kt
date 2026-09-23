package jp.co.soramitsu.common.data.network.config

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Refreshes only new-capability authority; legacy rollout/navigation state is independent. */
@Singleton
class MutationAuthorizationRefresher @Inject constructor(
    private val fetcher: RemoteConfigFetcher,
    private val authorization: MutationAuthorizationStore
) {
    private var job: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): Job {
        job?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            while (isActive) {
                refreshOnce()
                delay(REFRESH_MILLIS)
            }
        }.also { job = it }
    }

    suspend fun refreshOnce() {
        try {
            val config = withTimeout(30_000) { fetcher.getFeatureToggle() }
            authorization.acceptFresh(config.mutationAuthorization)
        } catch (_: TimeoutCancellationException) {
            authorization.invalidate()
        } catch (cancelled: CancellationException) {
            authorization.invalidate()
            throw cancelled
        } catch (_: Exception) {
            authorization.invalidate()
        }
    }

    companion object {
        const val REFRESH_MILLIS = 300_000L
    }
}
