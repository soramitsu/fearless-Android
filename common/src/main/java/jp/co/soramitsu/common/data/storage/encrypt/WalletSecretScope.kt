package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.core.models.Ecosystem
import org.bouncycastle.util.encoders.Hex

/**
 * The one signing secret of a wallet that a recovery item affects
 * (addendum U0.2). A quarantine, marker or missing key in one scope never
 * blocks another scope of the same wallet, and never another wallet.
 */
sealed interface WalletSecretScope {

    /** The wallet's root Substrate key (`<id>:SUBSTRATE_SECRETS`). */
    data object Substrate : WalletSecretScope

    /** The wallet's root Ethereum key (`<id>:ETHEREUM_SECRETS`). */
    data object Ethereum : WalletSecretScope

    /** The wallet's TON key (`<id>:TON_SECRETS`). */
    data object Ton : WalletSecretScope

    /**
     * A per-network account with its own key
     * (`<id>:<accountIdHex>:ACCESS_SECRETS`). Only 20- and 32-byte account
     * ids have such a namespace; a Bitcoin row (33-byte public key) or any
     * other id is derived from a root phrase and has no key of its own.
     */
    data class ChainAccount(val accountIdHex: String) : WalletSecretScope {
        init {
            require(accountIdHex.isNotEmpty() && accountIdHex == accountIdHex.lowercase()) {
                "A chain-account scope is named by its lowercase account id"
            }
        }

        val accountId: ByteArray
            get() = Hex.decode(accountIdHex)

        val hasSecretNamespace: Boolean
            get() = accountIdHex.length / 2 in CHAIN_ACCOUNT_SECRET_ID_BYTE_LENGTHS
    }

    companion object {

        /** Account-id lengths that own a `<id>:<accountIdHex>:ACCESS_SECRETS` key. */
        val CHAIN_ACCOUNT_SECRET_ID_BYTE_LENGTHS = setOf(20, 32)

        fun forEcosystem(ecosystem: Ecosystem): WalletSecretScope = when (ecosystem) {
            Ecosystem.Substrate -> Substrate
            Ecosystem.EthereumBased,
            Ecosystem.Ethereum -> Ethereum
            Ecosystem.Ton -> Ton
        }

        fun chainAccount(accountId: ByteArray): ChainAccount {
            require(accountId.isNotEmpty()) {
                "A chain-account scope requires an account id"
            }
            return ChainAccount(Hex.toHexString(accountId))
        }
    }
}

/** Why one scope of a wallet cannot sign today. */
enum class WalletRecoveryKind {

    /** The stored key failed signing integrity and was set aside, unchanged. */
    QUARANTINED,

    /** The wallet's recorded public identity for this key is malformed. */
    IDENTITY_MISMATCH,

    /**
     * The wallet records an identity for this scope and holds other keys,
     * but no key, quarantine or legacy payload exists for this one.
     */
    MISSING_KEY
}

/**
 * One item that needs attention. [sourceKey] is the preference key the item
 * is about (the active key name, never a value). Items are informational:
 * nothing about them stops the app from opening or other scopes from signing.
 */
data class WalletRecoveryItem(
    val metaId: Long,
    val scope: WalletSecretScope,
    val kind: WalletRecoveryKind,
    val sourceKey: String
) {
    /** A missing key cannot sign but is not stored state an earlier build flagged. */
    val isBlocking: Boolean
        get() = kind != WalletRecoveryKind.MISSING_KEY
}

/** Public identity of one wallet, enough to inventory its recovery state. */
class WalletRecoveryIdentity(
    val metaId: Long,
    val substratePublicKey: ByteArray?,
    val hasEthereumIdentity: Boolean,
    val hasTonIdentity: Boolean,
    val chainAccountIds: Collection<ByteArray>
) {
    val hasSubstrateIdentity: Boolean
        get() = substratePublicKey?.isNotEmpty() == true
}

/** Key names left in the recovery namespaces; no value is read. */
data class WalletRecoveryStateCounts(
    /** Quarantines and public-identity markers that still need attention. */
    val flaggedKeyCount: Int,
    /** Retained values: converted legacy payloads and keys of removed wallets. */
    val archivedKeyCount: Int
)
