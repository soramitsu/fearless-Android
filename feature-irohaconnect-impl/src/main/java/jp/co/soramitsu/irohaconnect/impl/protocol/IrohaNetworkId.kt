// The CRC-16/CCITT-FALSE checksum of Iroha hash literals.
// Infix bit operations keep explicit grouping.
@file:Suppress("MagicNumber", "UnnecessaryParentheses")

package jp.co.soramitsu.irohaconnect.impl.protocol

import jp.co.soramitsu.common.model.UniversalWalletRegistry

/**
 * The exact identity of one Iroha network: the marked 32-byte hash of its genesis header, written as the
 * checksummed literal `hash:<64 uppercase hex>#<CRC-16/CCITT-FALSE>` (iroha-js `NetworkId`).
 */
internal class IrohaNetworkId private constructor(private val value: ByteArray, val literal: String) {

    fun toBytes(): ByteArray = value.copyOf()

    override fun equals(other: Any?): Boolean = other is IrohaNetworkId && other.value.contentEquals(value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = literal

    companion object {
        const val BYTES = 32
        private val LITERAL = Regex("^hash:([0-9A-F]{64})#([0-9A-F]{4})$")

        /** Parses one exact canonical literal, or returns null. */
        fun parse(literal: String): IrohaNetworkId? {
            val match = LITERAL.matchEntire(literal) ?: return null
            val bytes = IrohaConnectEncoding.unhex(match.groupValues[1])
            if (!isMarked(bytes)) return null
            return fromBytes(bytes).takeIf { it.literal == literal }
        }

        fun fromBytes(bytes: ByteArray): IrohaNetworkId {
            require(isMarked(bytes)) { "NetworkId must be 32 marked hash bytes" }
            val body = IrohaConnectEncoding.hex(bytes).uppercase()
            return IrohaNetworkId(bytes.copyOf(), "hash:$body#${hashLiteralCrc("hash", body)}")
        }

        fun isMarked(bytes: ByteArray): Boolean = bytes.size == BYTES && (bytes[BYTES - 1].toInt() and 1) == 1

        private fun hashLiteralCrc(tag: String, body: String): String {
            var crc = 0xffff
            "$tag:$body".toByteArray(Charsets.UTF_8).forEach { byte ->
                crc = crc xor ((byte.toInt() and 0xff) shl 8)
                repeat(8) {
                    crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xffff else (crc shl 1) and 0xffff
                }
            }
            return crc.toString(16).uppercase().padStart(4, '0')
        }
    }
}

/** One network a dApp can pair with: its NetworkId, its own Torii root and its I105 address prefix. */
internal data class IrohaConnectProfile(
    val network: IrohaConnectNetwork,
    val chainId: String,
    val toriiBaseUrl: String,
    /** Null while the network's genesis NetworkId is not pinned: its links are refused. */
    val networkId: IrohaNetworkId?,
    val networkPrefix: Int
)

internal enum class IrohaConnectNetwork { TAIRA, NEXUS }

internal object IrohaConnectProfiles {
    /**
     * Genesis hash of the deployed Taira ledger (2026-10-22 reset), the same pin as the browser wallet's
     * `UNIVERSAL_WALLET_IROHA_NETWORKS.taira.networkId`. Update both after a Taira reset.
     */
    const val TAIRA_NETWORK_ID = "hash:4978D0BD15FC32B834672B27BBD8A03653B1DF060A1EEE7E9374D29AF3886D91#6774"

    /** Taira's native XOR asset definition, shown by symbol in reviews. */
    const val TAIRA_XOR_ASSET_DEFINITION = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"

    val taira = IrohaConnectProfile(
        network = IrohaConnectNetwork.TAIRA,
        chainId = UniversalWalletRegistry.taira.chainId,
        toriiBaseUrl = requireNotNull(UniversalWalletRegistry.taira.toriiBaseUrl),
        networkId = requireNotNull(IrohaNetworkId.parse(TAIRA_NETWORK_ID)),
        networkPrefix = UniversalWalletRegistry.taira.chainDiscriminant
    )

    /** SORA Nexus stays refused until its genesis NetworkId is pinned (its Torii also served a foreign certificate). */
    val nexus = IrohaConnectProfile(
        network = IrohaConnectNetwork.NEXUS,
        chainId = UniversalWalletRegistry.nexus.chainId,
        toriiBaseUrl = requireNotNull(UniversalWalletRegistry.nexus.toriiBaseUrl),
        networkId = null,
        networkPrefix = UniversalWalletRegistry.nexus.chainDiscriminant
    )

    val all = listOf(taira, nexus)

    val knownAssets: Map<String, String> = mapOf(TAIRA_XOR_ASSET_DEFINITION to "XOR")

    fun forNetworkId(networkId: IrohaNetworkId): IrohaConnectProfile? = all.firstOrNull { it.networkId == networkId }
}
