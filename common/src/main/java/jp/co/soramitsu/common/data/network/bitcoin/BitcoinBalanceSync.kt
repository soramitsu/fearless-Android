package jp.co.soramitsu.common.data.network.bitcoin

import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import java.lang.Math.addExact

class BitcoinBalanceSync(
    private val discovery: BitcoinReceiveDiscovery
) {
    suspend fun balance(
        mnemonic: String,
        passphrase: String = "",
        network: BitcoinKeyDerivation.Network = BitcoinKeyDerivation.Network.Mainnet,
        baseUrl: String? = null,
        gapLimit: Int? = null,
        maxLookahead: Int = BitcoinReceiveDiscovery.DEFAULT_MAX_LOOKAHEAD
    ): BitcoinBalanceSyncResult {
        val discoveryResult = discovery.discover(
            mnemonic = mnemonic,
            passphrase = passphrase,
            network = network,
            baseUrl = baseUrl,
            gapLimit = gapLimit,
            maxLookahead = maxLookahead
        )
        var confirmedSats = 0L
        var mempoolSats = 0L

        discoveryResult.addresses.forEach { address ->
            if (address.confirmedSats < 0 || address.mempoolSats < 0 || address.totalSats < 0) {
                throw BitcoinBalanceSyncException(BitcoinBalanceSyncException.Code.INVALID_ADDRESS_BALANCE)
            }

            confirmedSats = safeAdd(confirmedSats, address.confirmedSats)
            mempoolSats = safeAdd(mempoolSats, address.mempoolSats)
        }

        return BitcoinBalanceSyncResult(
            confirmedSats = confirmedSats,
            mempoolSats = mempoolSats,
            totalSats = safeAdd(confirmedSats, mempoolSats),
            usedAddresses = discoveryResult.usedAddresses,
            discovery = discoveryResult
        )
    }

    suspend fun accountBalance(
        mnemonic: String,
        passphrase: String = "",
        network: BitcoinKeyDerivation.Network = BitcoinKeyDerivation.Network.Mainnet,
        baseUrl: String? = null,
        gapLimit: Int? = null,
        maxLookahead: Int = BitcoinReceiveDiscovery.DEFAULT_MAX_LOOKAHEAD
    ): BitcoinAccountBalanceSyncResult {
        val accountDiscovery = discovery.discoverAccount(
            mnemonic = mnemonic,
            passphrase = passphrase,
            network = network,
            baseUrl = baseUrl,
            gapLimit = gapLimit,
            maxLookahead = maxLookahead
        )
        var confirmedSats = 0L
        var mempoolSats = 0L
        accountDiscovery.addresses.forEach { address ->
            if (address.confirmedSats < 0 || address.mempoolSats < 0 || address.totalSats < 0) {
                throw BitcoinBalanceSyncException(BitcoinBalanceSyncException.Code.INVALID_ADDRESS_BALANCE)
            }
            confirmedSats = safeAdd(confirmedSats, address.confirmedSats)
            mempoolSats = safeAdd(mempoolSats, address.mempoolSats)
        }

        return BitcoinAccountBalanceSyncResult(
            confirmedSats = confirmedSats,
            mempoolSats = mempoolSats,
            totalSats = safeAdd(confirmedSats, mempoolSats),
            discovery = accountDiscovery
        )
    }

    private fun safeAdd(left: Long, right: Long): Long {
        return try {
            addExact(left, right)
        } catch (_: ArithmeticException) {
            throw BitcoinBalanceSyncException(BitcoinBalanceSyncException.Code.BALANCE_OVERFLOW)
        }
    }
}

data class BitcoinBalanceSyncResult(
    val confirmedSats: Long,
    val mempoolSats: Long,
    val totalSats: Long,
    val usedAddresses: List<BitcoinReceiveDiscoveredAddress>,
    val discovery: BitcoinReceiveDiscoveryResult
)

data class BitcoinAccountBalanceSyncResult(
    val confirmedSats: Long,
    val mempoolSats: Long,
    val totalSats: Long,
    val discovery: BitcoinAccountDiscoveryResult
)

class BitcoinBalanceSyncException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_ADDRESS_BALANCE,
        BALANCE_OVERFLOW
    }
}
