package jp.co.soramitsu.nft.impl.domain.usecase

import kotlinx.coroutines.future.await
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigInteger
import javax.inject.Inject

class EstimateEthTransactionGasLimitUseCase @Inject constructor() {

    suspend operator fun invoke(
        web3j: Web3j,
        fromAddress: String,
        nonce: BigInteger,
        toAddress: String,
        data: String
    ): BigInteger {
        val txForFeeEstimation = getFakeTxForFeeEstimation(
            fromAddress = fromAddress,
            nonce = nonce,
            toAddress = toAddress,
            data = data
        )

        return web3j.ethEstimateGas(txForFeeEstimation).sendAsync().await().amountUsed
    }

    private fun getFakeTxForFeeEstimation(
        fromAddress: String,
        nonce: BigInteger,
        toAddress: String,
        data: String
    ): Transaction {
        return Transaction.createFunctionCallTransaction(
            /* from */ fromAddress,
            /* nonce */ nonce,
            /* gasPrice */ null,
            /* gasLimit */ null,
            /* to */ toAddress,
            /* value */ null,
            /* data */ data
        )
    }

}