package jp.co.soramitsu.common.data.storage.envelope

import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine

/**
 * The allowlist of preference keys whose values are wallet secrets and get
 * the inner envelope: mnemonics, seeds, private keys and keystore payloads in
 * their active, quarantine and archive copies, and the 1.x and 0.4 legacy
 * copies. Everything else is never sealed: the PIN and its history, TON
 * Connect and WalletConnect session keys, mutation journals, recovery
 * markers, the key attestation and every non-wallet preference.
 *
 * These are the namespaces emergency export reveals, so a sealed value is
 * always one that export can open again with the PIN.
 */
object WalletSecretSealPolicy {

    enum class Location { ACTIVE, QUARANTINE, ARCHIVE }

    /**
     * [logicalKey] is the active key the value belongs to, recorded in the
     * envelope header: the key itself, or the key a quarantine or archive key
     * is named after. A legacy quarantine name keyed by public key is its own
     * logical key, since its address-based original cannot be derived.
     */
    data class Target(
        val storageKey: String,
        val logicalKey: String,
        val scope: WalletSecretEnvelopeScope,
        val location: Location
    ) {
        /** Metadata id of the owning wallet, when the key names one. */
        val metaId: Long?
            get() = ROOT_KEY.matchEntire(logicalKey)?.groupValues?.get(1)?.toLongOrNull()
                ?: CHAIN_ACCOUNT_KEY.matchEntire(logicalKey)?.groupValues?.get(1)?.toLongOrNull()
                ?: LEGACY_QUARANTINE_NAME.matchEntire(logicalKey)?.groupValues?.get(2)?.toLongOrNull()
    }

    fun isSealable(storageKey: String): Boolean = classify(storageKey) != null

    fun classify(storageKey: String): Target? {
        if (storageKey.isEmpty() || storageKey.length > MAX_KEY_CHARS) return null
        val (location, named) = when {
            storageKey.startsWith(WalletSecretQuarantine.KEY_PREFIX) ->
                Location.QUARANTINE to storageKey.removePrefix(WalletSecretQuarantine.KEY_PREFIX)

            storageKey.startsWith(WalletSecretArchive.KEY_PREFIX) ->
                Location.ARCHIVE to (WalletSecretArchive.sourceKeyOf(storageKey) ?: return null)

            else -> Location.ACTIVE to storageKey
        }
        val scope = scopeOf(named, location) ?: return null
        return Target(storageKey, named, scope, location)
    }

    /** Key-name prefixes that can hold a sealable value, for name-only visitation. */
    val SCANNED_PREFIXES: Set<String> = (1..9).map(Int::toString).toSet() + setOf(
        WalletSecretQuarantine.KEY_PREFIX,
        WalletSecretArchive.KEY_PREFIX,
        LEGACY_V1_PREFIX,
        "private_",
        "seed_",
        "entropy_",
        "derivation_"
    )

    private fun scopeOf(named: String, location: Location): WalletSecretEnvelopeScope? {
        ROOT_KEY.matchEntire(named)?.let { match ->
            if (!match.groupValues[1].isCanonicalPositiveId()) return null
            return when (match.groupValues[2]) {
                "ACCESS_SECRETS" -> WalletSecretEnvelopeScope.LEGACY_META_ACCOUNT
                "SUBSTRATE_SECRETS" -> WalletSecretEnvelopeScope.SUBSTRATE_ROOT
                "ETHEREUM_SECRETS" -> WalletSecretEnvelopeScope.ETHEREUM_ROOT
                else -> WalletSecretEnvelopeScope.TON_ROOT
            }
        }
        CHAIN_ACCOUNT_KEY.matchEntire(named)?.let { match ->
            if (!match.groupValues[1].isCanonicalPositiveId()) return null
            return WalletSecretEnvelopeScope.CHAIN_ACCOUNT
        }
        LEGACY_QUARANTINE_NAME.matchEntire(named)?.let { match ->
            // Only a quarantine or archive copy is ever named this way.
            if (location == Location.ACTIVE) return null
            return if (match.groupValues[1] == "04") {
                WalletSecretEnvelopeScope.LEGACY_V04_SIGNING
            } else {
                WalletSecretEnvelopeScope.LEGACY_V1
            }
        }
        return when {
            named.startsWith(LEGACY_V1_PREFIX) && named.length > LEGACY_V1_PREFIX.length ->
                WalletSecretEnvelopeScope.LEGACY_V1

            LegacyWalletV04Secrets.isSigningDataKey(named) -> WalletSecretEnvelopeScope.LEGACY_V04_SIGNING
            LegacyWalletV04Secrets.isLegacyKey(named) -> WalletSecretEnvelopeScope.LEGACY_V04_TEXT
            else -> null
        }
    }

    private fun String.isCanonicalPositiveId(): Boolean =
        isNotEmpty() && first() != '0' && toLongOrNull()?.let { it > 0 } == true

    private const val LEGACY_V1_PREFIX = "security_source_"
    private const val MAX_KEY_CHARS = 1_024
    private val ROOT_KEY = Regex("^(\\d+):(ACCESS_SECRETS|SUBSTRATE_SECRETS|ETHEREUM_SECRETS|TON_SECRETS)$")
    private val CHAIN_ACCOUNT_KEY = Regex("^(\\d+):([0-9a-f]+):ACCESS_SECRETS$")
    private val LEGACY_QUARANTINE_NAME = Regex("^legacy_v(1|04)_(?:meta_(\\d+)_)?public_[0-9a-f]+$")
}
