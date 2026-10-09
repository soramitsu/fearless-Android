package jp.co.soramitsu.common.data.network.bitcoin

import java.math.BigInteger
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
        maxLookahead: Int = DEFAULT_MAX_LOOKAHEAD,
        change: Int = RECEIVE_BRANCH
    ): BitcoinReceiveDiscoveryResult {
        val resolvedGapLimit = gapLimit ?: defaultGapLimit(network)
        validateParams(mnemonic, resolvedGapLimit, maxLookahead)

        val indexerNetwork = network.toIndexerNetwork()
        val addresses = mutableListOf<BitcoinReceiveDiscoveredAddress>()
        var consecutiveUnused = 0
        var index = 0
        var lastUsedIndex: Int? = null

        while (consecutiveUnused < resolvedGapLimit && index < maxLookahead) {
            val path = BitcoinKeyDerivation.getReceivePath(
                network = network,
                index = index.toLong(),
                change = change.toLong()
            )
            val address = BitcoinKeyDerivation.deriveKey(
                mnemonic = mnemonic,
                passphrase = passphrase,
                derivationPath = path,
                network = network
            ).address
            val stats = client.address(address, indexerNetwork, baseUrl)
            val validatedBalance = validatedBalance(stats, expectedAddress = address)
            val txCount = transactionCount(stats)
            val used = txCount > 0
            val discovered = BitcoinReceiveDiscoveredAddress(
                address = address,
                index = index,
                path = path,
                confirmedSats = validatedBalance.confirmedSats,
                mempoolSats = validatedBalance.mempoolSats,
                totalSats = validatedBalance.totalSats,
                txCount = txCount,
                used = used,
                change = change
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
        val nextReceivePath = BitcoinKeyDerivation.getReceivePath(
            network = network,
            index = nextReceiveIndex.toLong(),
            change = change.toLong()
        )
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

    suspend fun discoverAccount(
        mnemonic: String,
        passphrase: String = "",
        network: BitcoinKeyDerivation.Network = BitcoinKeyDerivation.Network.Mainnet,
        baseUrl: String? = null,
        gapLimit: Int? = null,
        maxLookahead: Int = DEFAULT_MAX_LOOKAHEAD
    ): BitcoinAccountDiscoveryResult {
        return BitcoinAccountDiscoveryResult(
            receive = discover(
                mnemonic,
                passphrase,
                network,
                baseUrl,
                gapLimit,
                maxLookahead,
                change = RECEIVE_BRANCH
            ),
            change = discover(
                mnemonic,
                passphrase,
                network,
                baseUrl,
                gapLimit,
                maxLookahead,
                change = CHANGE_BRANCH
            )
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

    private fun validatedBalance(
        payload: BitcoinEsploraAddress,
        expectedAddress: String
    ): ValidatedAddressBalance {
        if (payload.address != expectedAddress ||
            !payload.chainStats.hasValidNonnegativeFields() ||
            !payload.mempoolStats.hasValidNonnegativeFields()
        ) {
            throw BitcoinReceiveDiscoveryException(BitcoinReceiveDiscoveryException.Code.INVALID_ADDRESS_PAYLOAD)
        }

        val confirmed = payload.chainStats.netSats()
        val mempool = payload.mempoolStats.netSats()
        val total = confirmed.add(mempool)
        if (confirmed.signum() < 0 || mempool.signum() < 0 || total.signum() < 0 ||
            total > BigInteger.valueOf(Long.MAX_VALUE)
        ) {
            throw BitcoinReceiveDiscoveryException(BitcoinReceiveDiscoveryException.Code.INVALID_ADDRESS_PAYLOAD)
        }

        return ValidatedAddressBalance(
            confirmedSats = confirmed.toLong(),
            mempoolSats = mempool.toLong(),
            totalSats = total.toLong()
        )
    }

    private fun BitcoinEsploraStats.hasValidNonnegativeFields(): Boolean {
        return fundedTxoCount >= 0 &&
            fundedTxoSum >= 0 &&
            spentTxoCount >= 0 &&
            spentTxoSum >= 0 &&
            txCount >= 0
    }

    private fun BitcoinEsploraStats.netSats(): BigInteger {
        return BigInteger.valueOf(fundedTxoSum).subtract(BigInteger.valueOf(spentTxoSum))
    }

    private data class ValidatedAddressBalance(
        val confirmedSats: Long,
        val mempoolSats: Long,
        val totalSats: Long
    )

    private fun defaultGapLimit(network: BitcoinKeyDerivation.Network): Int = when (network) {
        BitcoinKeyDerivation.Network.Mainnet -> UniversalWalletRegistry.bitcoinMainnet.defaultGapLimit
        BitcoinKeyDerivation.Network.Testnet -> UniversalWalletRegistry.bitcoinTestnet.defaultGapLimit
    }

    private fun BitcoinKeyDerivation.Network.toIndexerNetwork(): BitcoinIndexerRoutes.Network = when (this) {
        BitcoinKeyDerivation.Network.Mainnet -> BitcoinIndexerRoutes.Network.Mainnet
        BitcoinKeyDerivation.Network.Testnet -> BitcoinIndexerRoutes.Network.Testnet
    }

    companion object {
        const val RECEIVE_BRANCH = 0
        const val CHANGE_BRANCH = 1
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
    val used: Boolean,
    val change: Int = BitcoinReceiveDiscovery.RECEIVE_BRANCH
)

data class BitcoinAccountDiscoveryResult(
    val receive: BitcoinReceiveDiscoveryResult,
    val change: BitcoinReceiveDiscoveryResult
) {
    val addresses: List<BitcoinReceiveDiscoveredAddress> = receive.addresses + change.addresses
    val usedAddresses: List<BitcoinReceiveDiscoveredAddress> = receive.usedAddresses + change.usedAddresses
}

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
        INVALID_ADDRESS_PAYLOAD,
        LOOKAHEAD_EXHAUSTED
    }
}
