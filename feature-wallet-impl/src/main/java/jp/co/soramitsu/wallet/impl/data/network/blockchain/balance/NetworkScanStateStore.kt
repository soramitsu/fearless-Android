package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.model.NetworkScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class NetworkScanStateStore @Inject constructor(
    private val preferences: Preferences
) {
    /** Hydrates display state at construction, independently of whether a network scans again. */
    private val mutableStates = MutableStateFlow(restorePersistedStates())
    val states: StateFlow<Map<NetworkScanKey, NetworkScanState>> = mutableStates.asStateFlow()

    fun ensureCoverage(walletId: Long, chainId: String, coverage: AssetDiscoveryCoverage) {
        val key = NetworkScanKey(walletId, chainId)
        mutableStates.update { current ->
            val restored = current[key] ?: restore(key) ?: emptyState(key, coverage)
            val state = restored.copy(coverage = coverage)
            persist(key, state)
            current + (key to state)
        }
    }

    fun scanStarted(
        walletId: Long,
        chainId: String,
        coverage: AssetDiscoveryCoverage,
        attemptedAtMillis: Long = System.currentTimeMillis()
    ) {
        val key = NetworkScanKey(walletId, chainId)
        mutableStates.update { current ->
            val previous = current[key] ?: restore(key) ?: emptyState(key, coverage)
            val state = NetworkScanState(
                chainId = chainId,
                lastAttemptMillis = attemptedAtMillis,
                lastSuccessMillis = previous.lastSuccessMillis,
                isStale = previous.isStale,
                errorMessage = null,
                coverage = coverage
            )
            persist(key, state)
            current + (key to state)
        }
    }

    fun scanSucceeded(
        walletId: Long,
        chainId: String,
        coverage: AssetDiscoveryCoverage,
        succeededAtMillis: Long = System.currentTimeMillis()
    ) {
        val key = NetworkScanKey(walletId, chainId)
        mutableStates.update { current ->
            val previous = current[key] ?: restore(key) ?: emptyState(key, coverage)
            val state = NetworkScanState(
                chainId = chainId,
                lastAttemptMillis = previous.lastAttemptMillis ?: succeededAtMillis,
                lastSuccessMillis = succeededAtMillis,
                isStale = false,
                errorMessage = null,
                coverage = coverage
            )
            persist(key, state)
            current + (key to state)
        }
    }

    fun scanFailed(
        walletId: Long,
        chainId: String,
        coverage: AssetDiscoveryCoverage,
        errorMessage: String?,
        failedAtMillis: Long = System.currentTimeMillis()
    ) {
        val key = NetworkScanKey(walletId, chainId)
        mutableStates.update { current ->
            val previous = current[key] ?: restore(key) ?: emptyState(key, coverage)
            val state = NetworkScanState(
                chainId = chainId,
                lastAttemptMillis = failedAtMillis,
                lastSuccessMillis = previous.lastSuccessMillis,
                isStale = true,
                errorMessage = errorMessage,
                coverage = coverage
            )
            persist(key, state)
            current + (key to state)
        }
    }

    /** Marks an outer scheduler failure without downgrading coverage established by the loader. */
    fun scanFailedPreservingCoverage(
        walletId: Long,
        chainId: String,
        fallbackCoverage: AssetDiscoveryCoverage,
        errorMessage: String?,
        failedAtMillis: Long = System.currentTimeMillis()
    ) {
        val key = NetworkScanKey(walletId, chainId)
        val coverage = mutableStates.value[key]?.coverage
            ?: restore(key)?.coverage
            ?: fallbackCoverage
        scanFailed(walletId, chainId, coverage, errorMessage, failedAtMillis)
    }

    private fun restorePersistedStates(): Map<NetworkScanKey, NetworkScanState> {
        val preferenceKeys = preferences.keysWithPrefixes(
            prefixes = setOf(PREFERENCE_ROOT),
            maxResultCount = MAX_PERSISTED_FIELDS,
            maxKeyBytes = MAX_PREFERENCE_KEY_BYTES,
            maxTotalKeyBytes = MAX_TOTAL_PREFERENCE_KEY_BYTES
        )

        return preferenceKeys.asSequence()
            .mapNotNull(::scanKeyFromCoveragePreference)
            .distinct()
            .mapNotNull { key -> restore(key)?.let { key to it } }
            .toMap()
    }

    private fun restore(key: NetworkScanKey): NetworkScanState? {
        val prefix = preferencePrefix(key)
        val coverage = preferences.getString("$prefix.coverage")
            ?.let { stored -> AssetDiscoveryCoverage.entries.firstOrNull { it.name == stored } }
            ?: return null

        return NetworkScanState(
            chainId = key.chainId,
            lastAttemptMillis = preferences.getLong("$prefix.lastAttempt", 0L).takeIf { it > 0L },
            lastSuccessMillis = preferences.getLong("$prefix.lastSuccess", 0L).takeIf { it > 0L },
            isStale = preferences.getBoolean("$prefix.stale", false),
            errorMessage = preferences.getString("$prefix.error"),
            coverage = coverage
        )
    }

    private fun emptyState(
        key: NetworkScanKey,
        coverage: AssetDiscoveryCoverage
    ) = NetworkScanState(chainId = key.chainId, coverage = coverage)

    private fun persist(key: NetworkScanKey, state: NetworkScanState) {
        val prefix = preferencePrefix(key)
        preferences.putLong("$prefix.lastAttempt", state.lastAttemptMillis ?: 0L)
        preferences.putLong("$prefix.lastSuccess", state.lastSuccessMillis ?: 0L)
        preferences.putBoolean("$prefix.stale", state.isStale)
        preferences.putString("$prefix.error", state.errorMessage)
        preferences.putString("$prefix.coverage", state.coverage.name)
    }

    private fun preferencePrefix(key: NetworkScanKey): String {
        return "$PREFERENCE_ROOT${key.walletId}.${key.chainId}"
    }

    private fun scanKeyFromCoveragePreference(preferenceKey: String): NetworkScanKey? {
        if (!preferenceKey.startsWith(PREFERENCE_ROOT) || !preferenceKey.endsWith(COVERAGE_SUFFIX)) {
            return null
        }

        val storedIdentity = preferenceKey
            .removePrefix(PREFERENCE_ROOT)
            .removeSuffix(COVERAGE_SUFFIX)
        val walletSeparator = storedIdentity.indexOf('.')
        if (walletSeparator <= 0 || walletSeparator == storedIdentity.lastIndex) return null

        val walletId = storedIdentity.substring(0, walletSeparator).toLongOrNull() ?: return null
        val chainId = storedIdentity.substring(walletSeparator + 1)
        return NetworkScanKey(walletId, chainId)
    }

    private companion object {
        const val PREFERENCE_ROOT = "asset_discovery."
        const val COVERAGE_SUFFIX = ".coverage"
        const val MAX_PERSISTED_FIELDS = 8_192
        const val MAX_PREFERENCE_KEY_BYTES = 1_024
        const val MAX_TOTAL_PREFERENCE_KEY_BYTES = 1_048_576
    }
}
