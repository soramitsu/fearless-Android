package jp.co.soramitsu.account.impl.domain.recovery

import android.os.SystemClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault
import jp.co.soramitsu.account.impl.data.biometric.isWalletPin
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.PinResetSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.data.storage.envelope.PinVerification
import jp.co.soramitsu.common.data.storage.envelope.WalletPinAuthority
import jp.co.soramitsu.common.data.storage.envelope.WalletRootKeyKind
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretEnvelopeScope
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretPayloads
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

/** The proved backup has no wrap of the wallet key, so it cannot set a new PIN. Another wallet's backup may. */
class WalletPinRecoveryKeyUnavailableException :
    IllegalStateException("This backup cannot open the wallet key; prove another wallet's backup")

/**
 * One short-lived in-memory grant, bound to the exact old PIN and public wallet identity.
 *
 * Once the wallet keyring exists, a grant is authorized only with key
 * material: SK from the proved root's backup wrap, or from the PIN wrap opened
 * with the biometric vault's PIN. The new PIN then rewraps that SK. A grant
 * may also start for a readable PIN the user forgot ("Forgot PIN?").
 */
@Singleton
class WalletPinRecovery internal constructor(
    private val authority: WalletPinAuthority,
    private val keyring: WalletSecretKeyring,
    private val preferences: Preferences,
    private val accounts: AccountRepository,
    private val identities: WalletKeyIdentityReader,
    private val keyRecovery: WalletKeyRecovery,
    private val vault: BiometricPinVault,
    private val now: () -> Long
) {
    @Inject constructor(authority: WalletPinAuthority, keyring: WalletSecretKeyring, preferences: Preferences,
        accounts: AccountRepository, identities: WalletKeyIdentityReader, keyRecovery: WalletKeyRecovery,
        vault: BiometricPinVault) :
        this(authority, keyring, preferences, accounts, identities, keyRecovery, vault, SystemClock::elapsedRealtime)

    data class Choice(val name: String, val target: RecoveryTarget)
    private class Grant(
        val id: String,
        val pin: PinResetSnapshot,
        val expiresAt: Long,
        val health: PinHealth,
        val forgotten: Boolean
    ) {
        var authorized = false
        var identity: Pair<RecoveryTarget, String>? = null
        var key: WalletSecretKeyring.OpenedKey? = null

        fun release() {
            key?.close()
            key = null
        }
    }
    private val monitor = Any()
    private var grant: Grant? = null

    /** [forgotten] starts recovery for a readable PIN; otherwise only an unreadable PIN may be reset. */
    suspend fun begin(forgotten: Boolean = false): String = withContext(Dispatchers.IO) {
        val health = authority.health()
        check(health == PinHealth.UNREADABLE || (forgotten && health == PinHealth.HEALTHY))
        val snapshot = authority.captureResetSnapshot()
        coroutineContext.ensureActive()
        synchronized(monitor) {
            grant?.release()
            Grant(UUID.randomUUID().toString(), snapshot, now() + GRANT_LIFETIME_MS, health, forgotten).also { grant = it }.id
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

    /**
     * Only the BIOMETRIC_STRONG prompt callback may invoke this entry point, with the PIN its
     * vault unsealed. Once a keyring exists that PIN must open its PIN wrap; before that, an
     * enrolled vault proves the biometric only with its PIN, which must be a readable PIN's value.
     */
    suspend fun strongBiometricSucceeded(id: String, vaultPin: String? = null): Unit = withContext(Dispatchers.IO) {
        check(accounts.isBiometricEnabled())
        requireCurrent(id)
        val key = when {
            keyring.exists() -> {
                val pin = checkNotNull(vaultPin) { "The keyring needs the biometric vault's PIN" }
                when (val opening = keyring.openWithPin(pin)) {
                    is WalletSecretKeyring.PinOpening.Opened -> opening.key
                    else -> throw WalletPinRecoveryKeyUnavailableException()
                }
            }

            vault.isEnrolled() -> {
                val pin = checkNotNull(vaultPin) { "An enrolled vault proves the biometric only with its PIN" }
                check(isWalletPin(pin)) { "The vault holds no wallet PIN" }
                // A PIN this device can still read must be the one the vault holds.
                if (authority.health() == PinHealth.HEALTHY) {
                    check(authority.verifyForEmergency(pin).first == PinVerification.CORRECT) { "The vault holds another PIN" }
                }
                null
            }

            else -> null
        }
        authorize(id, key, identity = null)
    }

    suspend fun verifyWallet(id: String, target: RecoveryTarget, mode: ImportMode, material: String,
        path: String = "", password: String = ""): Unit = withContext(Dispatchers.Default) {
        requireCurrent(id)
        val identity = fingerprint(identities.read(target.metaId, target.scope()))
        val candidate = keyRecovery.prove(target.metaId, target.scope(), mode, material, path, password)
        // Once a keyring exists the proof must open SK; it then opens the sealed key K4 inspects.
        val key = if (keyring.exists()) openWithProof(candidate) ?: throw WalletPinRecoveryKeyUnavailableException() else null
        try {
            // K4 verifies the supplied backup against the durable identity and repairs
            // only that key when needed. Recovery input never enters this grant.
            keyRecovery.rebind(target.metaId, candidate, key)
            coroutineContext.ensureActive()
            check(fingerprint(identities.read(target.metaId, target.scope())) == identity)
            requireCurrent(id)
        } catch (failure: Throwable) {
            key?.close()
            throw failure
        }
        authorize(id, key, target to identity)
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
                authority.saveRecovered(pin, current.pin, current.key, current.forgotten)
                current.release()
                grant = null
                WalletViewOnlyAccess.pinResetCompleted()
            }
            PinAttemptLimiter(preferences).reset()
        }
    }

    fun cancel(id: String) = synchronized(monitor) {
        if (grant?.id == id) {
            grant?.release()
            grant = null
        }
    }

    private fun authorize(id: String, key: WalletSecretKeyring.OpenedKey?, identity: Pair<RecoveryTarget, String>?) {
        synchronized(monitor) {
            val current = try {
                currentLocked(id)
            } catch (failure: Exception) {
                key?.close()
                throw failure
            }
            current.authorized = true
            if (identity != null) current.identity = identity
            if (key != null) {
                current.key?.close()
                current.key = key
            }
        }
    }

    /** SK from the backup wrap of the root [candidate] proved, trying every canonical form of its key. */
    private fun openWithProof(candidate: VerifiedWalletSecret): WalletSecretKeyring.OpenedKey? {
        val scope = when (candidate.scope) {
            WalletSecretScope.Substrate -> WalletSecretEnvelopeScope.SUBSTRATE_ROOT
            WalletSecretScope.Ethereum -> WalletSecretEnvelopeScope.ETHEREUM_ROOT
            WalletSecretScope.Ton -> WalletSecretEnvelopeScope.TON_ROOT
            is WalletSecretScope.ChainAccount -> WalletSecretEnvelopeScope.CHAIN_ACCOUNT
        }
        val roots = WalletSecretPayloads.rootKeys(scope, candidate.encoded)
        try {
            val canonical = roots.flatMap { root -> WalletRootKeyKind.entries.mapNotNull { it.canonical(root.privateKey) } }
            try {
                return keyring.openWithBackup(canonical)
            } finally {
                canonical.forEach { it.fill(0) }
            }
        } finally {
            roots.forEach { it.privateKey.fill(0) }
        }
    }

    private fun requireCurrent(id: String) {
        val (expected, health) = synchronized(monitor) { currentLocked(id).let { it.pin to it.health } }
        check(authority.health() == health && expected.matches(authority.captureResetSnapshot())) {
            "The PIN changed while recovery was open"
        }
    }

    private fun currentLocked(id: String): Grant {
        val current = checkNotNull(grant)
        if (current.id == id && now() >= current.expiresAt) {
            current.release()
            grant = null
        }
        check(current.id == id && now() < current.expiresAt) { "PIN recovery authorization expired" }
        return current
    }

    private fun fingerprint(identity: WalletKeyIdentity): String = when (identity) {
        is WalletKeyIdentity.Substrate -> "substrate:${identity.cryptoType}:${Hex.toHexString(identity.publicKey)}:${Hex.toHexString(identity.accountId)}"
        is WalletKeyIdentity.Ethereum -> "ethereum:${Hex.toHexString(identity.publicKey)}:${Hex.toHexString(identity.address)}"
        is WalletKeyIdentity.Ton -> "ton:${Hex.toHexString(identity.publicKey)}"
        is WalletKeyIdentity.Chain -> "chain:${identity.cryptoType}:${Hex.toHexString(identity.publicKey)}:${Hex.toHexString(identity.accountId)}"
    }

    private companion object { const val GRANT_LIFETIME_MS = 10 * 60 * 1000L }
}
