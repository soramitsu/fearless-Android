package jp.co.soramitsu.common.data.network.nomis

import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val NOMIS_SCORING_ENABLED_KEY = "nomis_multichain_score_enabled"

/** Enforces the account-scoring preference for every caller, including background wallet sync. */
class PreferenceAwareNomisApi(
    private val delegate: NomisApi,
    private val preferences: Preferences
) : NomisApi {
    override suspend fun getNomisScore(address: String): NomisResponse = coroutineScope {
        val request = async(start = CoroutineStart.LAZY) {
            if (!preferences.getBoolean(NOMIS_SCORING_ENABLED_KEY, true)) {
                throw CancellationException("Account scoring is disabled")
            }
            delegate.getNomisScore(address)
        }
        val preferenceObserver = launch(start = CoroutineStart.UNDISPATCHED) {
            preferences.booleanFlow(NOMIS_SCORING_ENABLED_KEY, true).first { enabled -> !enabled }
            request.cancel(CancellationException("Account scoring is disabled"))
        }
        try {
            request.await()
        } finally {
            preferenceObserver.cancel()
        }
    }
}
