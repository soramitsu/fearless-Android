package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.impl.data.repository.MaxPriorityFeePerGas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Ethereum
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric

suspend fun Ethereum.fetchEthBalance(asset: Asset, address: String): BigInteger {
    return if (asset.isUtility) {
        runCatching {
            withContext(Dispatchers.IO) { ethGetBalance(address, DefaultBlockParameterName.LATEST).send() }
        }.getOrNull()?.balance.orZero()
    } else {
        val erc20GetBalanceFunction = Function(
            "balanceOf",
            listOf(Address(address)),
            emptyList()
        )

        val erc20BalanceWei = runCatching {
            withContext(Dispatchers.IO) {
                ethCall(
                    Transaction.createEthCallTransaction(
                        null,
                        asset.id,
                        FunctionEncoder.encode(erc20GetBalanceFunction)
                    ),
                    DefaultBlockParameterName.LATEST
                ).send().value
            }
        }.getOrNull()

        runCatching { Numeric.decodeQuantity(erc20BalanceWei) }.getOrNull().orZero()
    }
}

fun Web3jService.ethMaxPriorityFeePerGas(): Request<Any, MaxPriorityFeePerGas> {
    return Request<Any, MaxPriorityFeePerGas>(
        "eth_maxPriorityFeePerGas",
        emptyList(),
        this,
        MaxPriorityFeePerGas::class.java
    )
}
