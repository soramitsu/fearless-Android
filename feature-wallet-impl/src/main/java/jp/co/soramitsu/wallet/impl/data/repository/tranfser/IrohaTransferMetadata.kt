package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import java.util.Collections
import java.util.LinkedHashMap

/**
 * SDK-neutral transaction metadata accepted by the Iroha transfer signing seam.
 *
 * Construction is deliberately closed so callers cannot pass an arbitrary map to a signer.
 * Normal transfers use [empty]; wallet-smoke evidence must use one of the validated
 * [walletSmoke] factories.
 */
class IrohaTransferMetadata private constructor(entries: Map<String, String>) {

    private val entries = Collections.unmodifiableMap(LinkedHashMap(entries))

    /** Returns an immutable defensive copy suitable for a reviewed codec adapter. */
    fun asStringMap(): Map<String, String> {
        return Collections.unmodifiableMap(LinkedHashMap(entries))
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    override fun equals(other: Any?): Boolean {
        return other is IrohaTransferMetadata && entries == other.entries
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "IrohaTransferMetadata(keys=${entries.keys})"

    companion object {
        const val EVIDENCE_ROLE_KEY = "evidence_role"
        const val ROUTE_GOVERNANCE_ACTION_HASH_KEY = "route_governance_action_hash"
        const val WALLET_PLATFORM_KEY = "wallet_platform"
        const val WALLET_COMMIT_KEY = "wallet_commit"

        const val WALLET_SMOKE_ROLE = "wallet-smoke"
        const val ANDROID_PLATFORM = "android"
        const val NEXUS_NETWORK = "nexus"
        const val NEXUS_CHAIN_ID = "sora:nexus:global"

        private val ROUTE_GOVERNANCE_ACTION_HASH_PATTERN = Regex("sha256:[0-9a-f]{64}")
        private val WALLET_COMMIT_PATTERN = Regex("[0-9a-f]{40}")
        private val WALLET_SMOKE_KEYS = linkedSetOf(
            EVIDENCE_ROLE_KEY,
            ROUTE_GOVERNANCE_ACTION_HASH_KEY,
            WALLET_PLATFORM_KEY,
            WALLET_COMMIT_KEY
        )
        private val EMPTY = IrohaTransferMetadata(emptyMap())

        fun empty(): IrohaTransferMetadata = EMPTY

        /** Constructs the exact canonical Android wallet-smoke metadata contract. */
        fun walletSmoke(
            routeGovernanceActionHash: String,
            walletCommit: String
        ): IrohaTransferMetadata {
            return walletSmoke(
                linkedMapOf(
                    EVIDENCE_ROLE_KEY to WALLET_SMOKE_ROLE,
                    ROUTE_GOVERNANCE_ACTION_HASH_KEY to routeGovernanceActionHash,
                    WALLET_PLATFORM_KEY to ANDROID_PLATFORM,
                    WALLET_COMMIT_KEY to walletCommit
                )
            )
        }

        /**
         * Validates an operator/evidence map before it can become a signing request.
         * Every key and value must be a JSON string and the key set must be exact.
         */
        fun walletSmoke(untrustedEntries: Map<*, *>): IrohaTransferMetadata {
            val snapshot = LinkedHashMap<Any?, Any?>()
            untrustedEntries.forEach { (key, value) -> snapshot[key] = value }

            require(snapshot.keys == WALLET_SMOKE_KEYS) {
                "wallet-smoke metadata must contain exactly $WALLET_SMOKE_KEYS"
            }
            require(snapshot.values.all { it is String }) {
                "wallet-smoke metadata values must all be JSON strings"
            }

            val evidenceRole = snapshot[EVIDENCE_ROLE_KEY] as String
            val routeGovernanceActionHash = snapshot[ROUTE_GOVERNANCE_ACTION_HASH_KEY] as String
            val walletPlatform = snapshot[WALLET_PLATFORM_KEY] as String
            val walletCommit = snapshot[WALLET_COMMIT_KEY] as String

            require(evidenceRole == WALLET_SMOKE_ROLE) {
                "wallet-smoke evidence_role must be exactly $WALLET_SMOKE_ROLE"
            }
            require(routeGovernanceActionHash.matches(ROUTE_GOVERNANCE_ACTION_HASH_PATTERN)) {
                "wallet-smoke route_governance_action_hash must be canonical sha256 lowercase hex"
            }
            require(routeGovernanceActionHash.removePrefix("sha256:").any { it != '0' }) {
                "wallet-smoke route_governance_action_hash must not be the all-zero sentinel"
            }
            require(walletPlatform == ANDROID_PLATFORM) {
                "wallet-smoke wallet_platform must be exactly $ANDROID_PLATFORM"
            }
            require(walletCommit.matches(WALLET_COMMIT_PATTERN)) {
                "wallet-smoke wallet_commit must be canonical 40-character lowercase git hex"
            }
            require(walletCommit.any { it != '0' }) {
                "wallet-smoke wallet_commit must not be the all-zero sentinel"
            }

            return IrohaTransferMetadata(
                linkedMapOf(
                    EVIDENCE_ROLE_KEY to evidenceRole,
                    ROUTE_GOVERNANCE_ACTION_HASH_KEY to routeGovernanceActionHash,
                    WALLET_PLATFORM_KEY to walletPlatform,
                    WALLET_COMMIT_KEY to walletCommit
                )
            )
        }
    }
}

/** Explicit non-broadcast construction path for a canonical Android wallet-smoke request. */
fun IrohaTransferSigningRequest.withWalletSmokeMetadata(
    routeGovernanceActionHash: String,
    walletCommit: String
): IrohaTransferSigningRequest {
    requireNexusWalletSmokeContext()
    return copy(
        transactionMetadata = IrohaTransferMetadata.walletSmoke(
            routeGovernanceActionHash = routeGovernanceActionHash,
            walletCommit = walletCommit
        )
    )
}

/** Validates untrusted wallet-smoke metadata before a signer or Torii call can be selected. */
fun IrohaTransferSigningRequest.withWalletSmokeMetadata(
    untrustedEntries: Map<*, *>
): IrohaTransferSigningRequest {
    requireNexusWalletSmokeContext()
    return copy(transactionMetadata = IrohaTransferMetadata.walletSmoke(untrustedEntries))
}

/** Attaches an already-snapshotted wallet-smoke value after route/context resolution. */
internal fun IrohaTransferSigningRequest.withValidatedWalletSmokeMetadata(
    validatedMetadata: IrohaTransferMetadata
): IrohaTransferSigningRequest {
    requireNexusWalletSmokeContext()
    require(!validatedMetadata.isEmpty()) {
        "validated wallet-smoke metadata must not be empty"
    }
    return copy(transactionMetadata = validatedMetadata)
}

private fun IrohaTransferSigningRequest.requireNexusWalletSmokeContext() {
    require(network == IrohaTransferMetadata.NEXUS_NETWORK) {
        "wallet-smoke metadata requires network=nexus"
    }
    require(chainId == IrohaTransferMetadata.NEXUS_CHAIN_ID) {
        "wallet-smoke metadata requires chainId=sora:nexus:global"
    }
}
