package jp.co.soramitsu.common.data.secrets

import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthentication
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthenticator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One screen instance's authorization only. No PIN, selected value or page is
 * retained here. The screen must revoke on stop and create a new session on recreation.
 * Run storage operations off the main thread; cancellation and revocation both
 * prevent a late operation from returning recovery data to the screen.
 */
class WalletEmergencyRecoverySession(
    private val authenticator: WalletEmergencyAuthenticator,
    private val reader: WalletEmergencyRecoveryReader
) {
    private val guard = Any()
    private var generation = 0L
    private var authenticatedGeneration: Long? = null
    // SK from the PIN wrap, held only while this screen's authorization is current.
    private var walletKey: WalletSecretKeyring.OpenedKey? = null

    fun revoke() {
        synchronized(guard) {
            generation++
            authenticatedGeneration = null
            walletKey?.close()
            walletKey = null
        }
    }

    suspend fun unlock(code: String): EmergencyRecoveryAccessResult {
        currentCoroutineContext().ensureActive()
        val requested = synchronized(guard) {
            generation++
            authenticatedGeneration = null
            walletKey?.close()
            walletKey = null
            generation
        }
        val (result, key) = authenticator.authenticateWithKey(code)
        try {
            requireCurrent(requested)
        } catch (failure: Throwable) {
            key?.close()
            throw failure
        }
        return when (result) {
            WalletEmergencyAuthentication.Authenticated -> {
                val page = try {
                    reader.listPage().also { currentCoroutineContext().ensureActive() }
                } catch (failure: Throwable) {
                    key?.close()
                    throw failure
                }
                synchronized(guard) {
                    try {
                        requireCurrentLocked(requested)
                    } catch (failure: Throwable) {
                        key?.close()
                        throw failure
                    }
                    authenticatedGeneration = requested
                    walletKey = key
                }
                EmergencyRecoveryAccessResult.Page(page)
            }
            WalletEmergencyAuthentication.Unavailable -> {
                val hasKeys = reader.hasStoredKeys()
                requireCurrent(requested)
                if (hasKeys) EmergencyRecoveryAccessResult.Refused(result) else EmergencyRecoveryAccessResult.EmptyWithoutAuthentication
            }
            else -> EmergencyRecoveryAccessResult.Refused(result)
        }
    }

    suspend fun listPage(afterStorageKey: String? = null): EmergencyRecoveryPage {
        val requested = requireAuthentication()
        val page = reader.listPage(afterStorageKey)
        requireCurrent(requested)
        return page
    }

    suspend fun readKey(storageKey: String): EmergencyRecoveryEntry {
        val requested = requireAuthentication()
        val key = synchronized(guard) { walletKey?.let { WalletSecretKeyring.OpenedKey(it.keyId, it.key) } }
        val entry = try {
            reader.readKey(storageKey, key)
        } finally {
            key?.close()
        }
        requireCurrent(requested)
        return entry
    }

    private fun requireAuthentication(): Long = synchronized(guard) {
        checkNotNull(authenticatedGeneration) { "Emergency recovery requires authentication" }
            .also(::requireCurrentLocked)
    }

    private suspend fun requireCurrent(requested: Long) {
        currentCoroutineContext().ensureActive()
        synchronized(guard) { requireCurrentLocked(requested) }
    }

    private fun requireCurrentLocked(requested: Long) {
        if (requested != generation) throw CancellationException("Emergency recovery session was revoked")
    }
}

sealed interface EmergencyRecoveryAccessResult {
    data class Page(val page: EmergencyRecoveryPage) : EmergencyRecoveryAccessResult
    data class Refused(val result: WalletEmergencyAuthentication) : EmergencyRecoveryAccessResult
    data object EmptyWithoutAuthentication : EmergencyRecoveryAccessResult
}
