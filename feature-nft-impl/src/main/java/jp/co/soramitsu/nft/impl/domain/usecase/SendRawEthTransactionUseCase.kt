package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.shared_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.shared_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.shared_utils.encrypt.Signer
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.future.await
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import javax.inject.Inject

class SendRawEthTransactionUseCase @Inject constructor() {

    suspend operator fun invoke(
        web3j: Web3j,
        keypair: Keypair,
        transaction: RawTransaction,
        ethereumChainId: Long,
    ): String {
        val signedRawTransaction = signTransaction(transaction, keypair, ethereumChainId)

        val txResult = web3j.ethSendRawTransaction(signedRawTransaction).sendAsync().await()

        return txResult.transactionHash ?: throw IllegalStateException(
            """
                Error while executing ethSendRawTransaction, message - ${txResult.error.message}
            """.trimIndent()
        )
    }

    private fun signTransaction(
        transaction: RawTransaction,
        keypair: Keypair,
        ethereumChainId: Long
    ): String {
        val encodedTx = TransactionEncoder.encode(transaction, ethereumChainId)

        val signatureData = Signer.sign(
            multiChainEncryption = MultiChainEncryption.Ethereum,
            message = encodedTx,
            keypair = keypair
        ).toSignatureData()

        val eip155SignatureData: Sign.SignatureData = TransactionEncoder.createEip155SignatureData(
            /* signatureData */ signatureData,
            /* chainId */ ethereumChainId
        )

        return transaction.encodeWith(eip155SignatureData).toHexString(withPrefix = true)
    }

    private fun SignatureWrapper.toSignatureData(): Sign.SignatureData {
        require(this is SignatureWrapper.Ecdsa)

        return Sign.SignatureData(v, r, s)
    }

    private fun RawTransaction.encodeWith(signatureData: Sign.SignatureData): ByteArray {
        val values = TransactionEncoder.asRlpValues(
            /* rawTransaction */ this,
            /* signatureData */ signatureData
        )

        return RlpEncoder.encode(RlpList(values))
    }

}