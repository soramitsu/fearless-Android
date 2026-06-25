package jp.co.soramitsu.common.data.network.bitcoin

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation

class BitcoinReceiveDiscovery(
    private val client: BitcoinIndexerClient
) {
    suspend fun discover(
        mnemonic: String,
        passphrase: String = "",
        network: BitcoinKeyDerivation.Network = BitcoinKeyDerivation.Network.Mainnet,
        baseUrl: String? = null,
        gapLimit: Int? = null,
        maxLookahead: Int = DEFAULT_MAX_LOOKAHEAD
    ): BitcoinReceiveDiscoveryResult {
        val resolvedGapLimit = gapLimit ?: defaultGapLimit(network)
        validateParams(mnemonic, resolvedGapLimit, maxLookahead)

        val indexerNetwork = network.toIndexerNetwork()
        val addresses = mutableListOf<BitcoinReceiveDiscoveredAddress>()
        var consecutiveUnused = 0
        var index = 0
        var lastUsedIndex: Int? = null

        while (consecutiveUnused < resolvedGapLimit && index < maxLookahead) {
            val path = BitcoinKeyDerivation.getReceivePath(network = network, index = index.toLong())
            val address = BitcoinKeyDerivation.deriveKey(
                mnemonic = mnemonic,
                passphrase = passphrase,
                derivationPath = path,
                network = network
            ).address
            val stats = client.address(address, indexerNetwork, baseUrl)
            val txCount = transactionCount(stats)
            val used = txCount > 0
            val discovered = BitcoinReceiveDiscoveredAddress(
                address = address,
                index = index,
                path = path,
                confirmedSats = stats.confirmedSats,
                mempoolSats = stats.mempoolSats,
                totalSats = stats.totalSats,
                txCount = txCount,
                used = used
            )

            addresses += discovered

            if (used) {
                lastUsedIndex = index
                consecutiveUnused = 0
            } else {
                consecutiveUnused += 1
            }

            index += 1
        }

        if (consecutiveUnused < resolvedGapLimit) {
            throw BitcoinReceiveDiscoveryException(BitcoinReceiveDiscoveryException.Code.LOOKAHEAD_EXHAUSTED)
        }

        val nextReceiveIndex = (lastUsedIndex ?: -1) + 1
        val nextReceivePath = BitcoinKeyDerivation.getReceivePath(network = network, index = nextReceiveIndex.toLong())
        val nextReceiveAddress = BitcoinKeyDerivation.deriveKey(
            mnemonic = mnemonic,
            passphrase = passphrase,
            derivationPath = nextReceivePath,
            network = network
        ).address

        return BitcoinReceiveDiscoveryResult(
            addresses = addresses,
            gapLimit = resolvedGapLimit,
            lastUsedIndex = lastUsedIndex,
            nextReceiveAddress = nextReceiveAddress,
            nextReceiveIndex = nextReceiveIndex,
            usedAddresses = addresses.filter { it.used }
        )
    }

    private fun validateParams(mnemonic: String, gapLimit: Int, maxLookahead: Int) {
        if (mnemonic.isBlank()) {
            throw BitcoinReceiveDiscoveryException(BitcoinReceiveDiscoveryException.Code.MNEMONIC_REQUIRED)
        }
        if (gapLimit <= 0 || gapLimit > MAX_GAP_LIMIT) {
            throw BitcoinReceiveDiscoveryException(BitcoinReceiveDiscoveryException.Code.INVALID_GAP_LIMIT)
        }
        if (maxLookahead < gapLimit || maxLookahead > MAX_LOOKAHEAD) {
            throw BitcoinReceiveDiscoveryException(BitcoinReceiveDiscoveryException.Code.INVALID_MAX_LOOKAHEAD)
        }
    }

    private fun transactionCount(address: BitcoinEsploraAddress): Int {
        val chainCount = address.chainStats.txCount
        val mempoolCount = address.mempoolStats.txCount
        val total = chainCount.toLong() + mempoolCount.toLong()

        if (chainCount < 0 || mempoolCount < 0 || total < 0 || total > Int.MAX_VALUE.toLong()) {
            throw BitcoinReceiveDiscoveryException(BitcoinReceiveDiscoveryException.Code.INVALID_TRANSACTION_COUNT)
        }

        return total.toInt()
    }

    private fun defaultGapLimit(network: BitcoinKeyDerivation.Network): Int = when (network) {
        BitcoinKeyDerivation.Network.Mainnet -> UniversalWalletRegistry.bitcoinMainnet.defaultGapLimit
        BitcoinKeyDerivation.Network.Testnet -> UniversalWalletRegistry.bitcoinTestnet.defaultGapLimit
    }

    private fun BitcoinKeyDerivation.Network.toIndexerNetwork(): BitcoinIndexerRoutes.Network = when (this) {
        BitcoinKeyDerivation.Network.Mainnet -> BitcoinIndexerRoutes.Network.Mainnet
        BitcoinKeyDerivation.Network.Testnet -> BitcoinIndexerRoutes.Network.Testnet
    }

    companion object {
        const val DEFAULT_MAX_LOOKAHEAD = 1_000
        const val MAX_GAP_LIMIT = 100
        const val MAX_LOOKAHEAD = 10_000
    }
}

data class BitcoinReceiveDiscoveredAddress(
    val address: String,
    val index: Int,
    val path: String,
    val confirmedSats: Long,
    val mempoolSats: Long,
    val totalSats: Long,
    val txCount: Int,
    val used: Boolean
)

data class BitcoinReceiveDiscoveryResult(
    val addresses: List<BitcoinReceiveDiscoveredAddress>,
    val gapLimit: Int,
    val lastUsedIndex: Int?,
    val nextReceiveAddress: String,
    val nextReceiveIndex: Int,
    val usedAddresses: List<BitcoinReceiveDiscoveredAddress>
)

class BitcoinReceiveDiscoveryException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        MNEMONIC_REQUIRED,
        INVALID_GAP_LIMIT,
        INVALID_MAX_LOOKAHEAD,
        INVALID_TRANSACTION_COUNT,
        LOOKAHEAD_EXHAUSTED
    }
}
