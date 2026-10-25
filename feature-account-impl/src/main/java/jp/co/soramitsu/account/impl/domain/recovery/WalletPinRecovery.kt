package jp.co.soramitsu.account.impl.domain.recovery

import android.os.SystemClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.PinResetSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.data.storage.pin.PinAttemptLimiter
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex
import kotlin.coroutines.coroutineContext

/** One short-lived in-memory grant, bound to the exact old PIN and public wallet identity. */
@Singleton
class WalletPinRecovery internal constructor(
    private val encrypted: EncryptedPreferences,
    private val preferences: Preferences,
    private val accounts: AccountRepository,
    private val identities: WalletKeyIdentityReader,
    private val keyRecovery: WalletKeyRecovery,
    private val now: () -> Long
) {
    @Inject constructor(encrypted: EncryptedPreferences, preferences: Preferences, accounts: AccountRepository,
        identities: WalletKeyIdentityReader, keyRecovery: WalletKeyRecovery) :
        this(encrypted, preferences, accounts, identities, keyRecovery, SystemClock::elapsedRealtime)

    data class Choice(val name: String, val target: RecoveryTarget)
    private class Grant(val id: String, val pin: PinResetSnapshot, val expiresAt: Long) {
        var authorized = false
        var identity: Pair<RecoveryTarget, String>? = null
    }
    private val monitor = Any()
    private var grant: Grant? = null

    suspend fun begin(): String = withContext(Dispatchers.IO) {
        check(encrypted.getPinHealth() == PinHealth.UNREADABLE)
        val snapshot = encrypted.capturePinResetSnapshot()
        coroutineContext.ensureActive()
        synchronized(monitor) {
            Grant(UUID.randomUUID().toString(), snapshot, now() + GRANT_LIFETIME_MS).also { grant = it }.id
        }
    }

    suspend fun choices(id: String): List<Choice> = withContext(Dispatchers.IO) {
        requireCurrent(id)
        buildList {
            accounts.lightMetaAccountsFlow().first().forEach { wallet ->
                val scopes = listOf(WalletSecretScope.Substrate, WalletSecretScope.Ethereum, WalletSecretScope.Ton) +
                    wallet.universalWalletChainAccounts.values.map { WalletSecretScope.chainAccount(it.accountId) }
                        .filter { it.hasSecretNamespace }
                scopes.distinct().forEach { scope ->
                    try {
                        identities.read(wallet.id, scope)
                        add(Choice(wallet.name, RecoveryTarget.from(wallet.id, scope)))
                    } catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { /* Another independently usable identity may still prove ownership. */ }
                }
            }
        }.also { requireCurrent(id) }
    }

    /** Only the BIOMETRIC_STRONG prompt callback may invoke this entry point. */
    suspend fun strongBiometricSucceeded(id: String): Unit = withContext(Dispatchers.IO) {
        check(accounts.isBiometricEnabled())
        requireCurrent(id)
        synchronized(monitor) { currentLocked(id).authorized = true }
    }

    suspend fun verifyWallet(id: String, target: RecoveryTarget, mode: ImportMode, material: String,
        path: String = "", password: String = ""): Unit = withContext(Dispatchers.Default) {
        requireCurrent(id)
        val identity = fingerprint(identities.read(target.metaId, target.scope()))
        // K4 verifies the supplied backup against the durable identity and repairs
        // only that key when needed. Recovery input never enters this grant.
        keyRecovery.restore(target.metaId, target.scope(), mode, material, path, password)
        coroutineContext.ensureActive()
        check(fingerprint(identities.read(target.metaId, target.scope())) == identity)
        requireCurrent(id)
        synchronized(monitor) {
            currentLocked(id).also { it.authorized = true; it.identity = target to identity }
            Unit
        }
    }

    fun isActive(id: String): Boolean = synchronized(monitor) {
        grant?.let { it.id == id && now() < it.expiresAt } == true
    }

    fun isAuthorized(id: String): Boolean = synchronized(monitor) {
        grant?.let { it.id == id && now() < it.expiresAt && it.authorized } == true
    }

    suspend fun saveNewPin(id: String, pin: String) = withContext(Dispatchers.IO) {
        WalletCrossStoreMutationMutex.instance.withLock {
            requireCurrent(id)
            val proof = synchronized(monitor) { currentLocked(id).also { check(it.authorized) }.identity }
            proof?.let { (target, identity) ->
                check(fingerprint(identities.read(target.metaId, target.scope())) == identity) { "The verified wallet changed" }
            }
            coroutineContext.ensureActive()
            synchronized(monitor) {
                val current = currentLocked(id)
                check(current.authorized)
                encrypted.saveRecoveredPinCodeDurably(pin, current.pin)
                grant = null
                WalletViewOnlyAccess.pinResetCompleted()
            }
            PinAttemptLimiter(preferences).reset()
        }
    }

    fun cancel(id: String) = synchronized(monitor) {
        if (grant?.id == id) grant = null
    }

    private fun requireCurrent(id: String) {
        val expected = synchronized(monitor) { currentLocked(id).pin }
        check(encrypted.getPinHealth() == PinHealth.UNREADABLE && expected.matches(encrypted.capturePinResetSnapshot())) {
            "The PIN changed while recovery was open"
        }
    }

    private fun currentLocked(id: String): Grant = checkNotNull(grant).also {
        check(it.id == id && now() < it.expiresAt) { "PIN recovery authorization expired" }
    }

    private fun fingerprint(identity: WalletKeyIdentity): String = when (identity) {
        is WalletKeyIdentity.Substrate -> "substrate:${identity.cryptoType}:${Hex.toHexString(identity.publicKey)}:${Hex.toHexString(identity.accountId)}"
        is WalletKeyIdentity.Ethereum -> "ethereum:${Hex.toHexString(identity.publicKey)}:${Hex.toHexString(identity.address)}"
        is WalletKeyIdentity.Ton -> "ton:${Hex.toHexString(identity.publicKey)}"
        is WalletKeyIdentity.Chain -> "chain:${identity.cryptoType}:${Hex.toHexString(identity.publicKey)}:${Hex.toHexString(identity.accountId)}"
    }

    private companion object { const val GRANT_LIFETIME_MS = 10 * 60 * 1000L }
}
