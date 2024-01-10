package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.nft.impl.domain.utils.getNonce
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger
import javax.inject.Inject

class CreateRawEthTransactionUseCase @Inject constructor(
    private val estimateEthTransactionGasLimitUseCase: EstimateEthTransactionGasLimitUseCase
) {

    suspend operator fun invoke(
        web3j: Web3j,
        fromAddress: String,
        toAddress: String,
        data: String?,
        value: BigInteger?,
        nonce: BigInteger?,
        gasLimit: BigInteger?,
        gasPrice: BigInteger?,
    ): RawTransaction {
        val finalNonce = nonce ?: web3j.getNonce(fromAddress)
        val finalGasPrice = gasPrice ?: DefaultGasProvider.GAS_PRICE

        val dataOrDefault = data.orEmpty()

        val finalGasLimit =
            gasLimit ?: estimateEthTransactionGasLimitUseCase(
                web3j = web3j,
                fromAddress = fromAddress,
                nonce = finalNonce,
                toAddress = toAddress,
                data = dataOrDefault
            )

        return RawTransaction.createTransaction(
            finalNonce,
            finalGasPrice,
            finalGasLimit,
            toAddress,
            value,
            dataOrDefault
        )
    }

}