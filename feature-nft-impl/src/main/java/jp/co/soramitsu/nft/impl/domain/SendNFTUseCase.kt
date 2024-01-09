package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.nft.impl.domain.models.NFTTransferParams
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.shared_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.shared_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.shared_utils.encrypt.Signer
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.future.await
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Array
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.utils.Convert
import java.math.BigInteger

class SendNFTUseCase(
    private val accountRepository: AccountRepository,
    private val ethereumConnectionPool: EthereumConnectionPool
) {

    internal suspend operator fun invoke(
        chain: Chain,
        params: NFTTransferParams
    ): Result<String> {
        val chainId = chain.id

        val connection = ethereumConnectionPool.get(chainId)
            ?: return Result.failure(
                """
                        There is no connection created for chain $chainId
                    """.trimIndent()
            )

        val web3 = connection.web3j
            ?: return Result.failure(
                """
                        There is no connection established for chain $chainId
                    """.trimIndent()
            )

        return runCatching {
            val ethereumSecrets =
                accountRepository.getMetaAccountSecrets(
                    metaId = accountRepository.getSelectedMetaAccount().id
                )?.get(MetaAccountSecrets.EthereumKeypair) ?: error(
                    """
                        Public or Private key is not available.
                    """.trimIndent()
                )

            val (publicKey, privateKey) = ethereumSecrets.run {
                get(KeyPairSchema.PublicKey) to get(KeyPairSchema.PrivateKey)
            }

            val rawTransaction = formTransaction(
                fromAddress = params.sender,
                toAddress = params.receiver,
                data = FunctionEncoder.encode(transferFunction(params)),
                value = null,
                nonce = null,
                gasLimit = null,
                gasPrice = null,
                web3 = web3
            )

            sendTransaction(
                transaction = rawTransaction,
                keypair = Keypair(publicKey,  privateKey),
                ethereumChainId = chainId.toLong(),
                web3 = web3
            )
        }
    }

    private fun transferFunction(
        params: NFTTransferParams
    ): Function {
        return when(params) {
            is NFTTransferParams.ERC721 -> Function(
                "safeTransferFrom",
                listOf(
                    Address(params.sender),
                    Address(params.receiver),
                    Uint256(params.tokenId),
                    DynamicBytes(params.data)
                ),
                listOf(TypeReference.create(Array::class.java))
            )
            is NFTTransferParams.ERC1155 -> Function(
                "safeTransferFrom",
                listOf(
                    Address(params.sender),
                    Address(params.receiver),
                    Uint256(params.tokenId),
                    Uint256(Convert.toWei(params.amount, Convert.Unit.ETHER).toBigInteger()),
                    DynamicBytes(params.data)
                ),
                listOf(TypeReference.create(Array::class.java))
            )
        }
    }

    private suspend fun formTransaction(
        fromAddress: String,
        toAddress: String,
        data: String?,
        value: BigInteger?,
        nonce: BigInteger?,
        gasLimit: BigInteger?,
        gasPrice: BigInteger?,
        web3: Web3j
    ): RawTransaction {
        val finalNonce = nonce ?: getNonce(web3, fromAddress)
        val finalGasPrice = gasPrice ?: DefaultGasProvider.GAS_PRICE

        val dataOrDefault = data.orEmpty()

        val finalGasLimit = gasLimit ?: run {
            val forFeeEstimatesTx = Transaction.createFunctionCallTransaction(
                fromAddress,
                finalNonce,
                null,
                null,
                toAddress,
                value,
                dataOrDefault
            )

            estimateGasLimit(web3, forFeeEstimatesTx)
        }

        return RawTransaction.createTransaction(
            finalNonce,
            finalGasPrice,
            finalGasLimit,
            toAddress,
            value,
            dataOrDefault
        )
    }

    private suspend fun sendTransaction(
        transaction: RawTransaction,
        keypair: Keypair,
        ethereumChainId: Long,
        web3: Web3j
    ): String {
        val signedRawTransaction = signTransaction(transaction, keypair, ethereumChainId)

        val txResult = web3.ethSendRawTransaction(signedRawTransaction).sendAsync().await()

        return txResult.transactionHash ?:
            throw IllegalStateException(
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
        val signatureData = Signer.sign(MultiChainEncryption.Ethereum, encodedTx, keypair).toSignatureData()

        val eip155SignatureData: Sign.SignatureData = TransactionEncoder.createEip155SignatureData(signatureData, ethereumChainId)

        return transaction.encodeWith(eip155SignatureData).toHexString(withPrefix = true)
    }

    private suspend fun getNonce(web3: Web3j, address: String): BigInteger {
        return web3.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
            .sendAsync().await()
            .transactionCount
    }

    private suspend fun estimateGasLimit(web3: Web3j, tx: Transaction): BigInteger {
        return web3.ethEstimateGas(tx).sendAsync().await().amountUsed
    }

    private fun SignatureWrapper.toSignatureData(): Sign.SignatureData {
        require(this is SignatureWrapper.Ecdsa)

        return Sign.SignatureData(v, r, s)
    }

    private fun RawTransaction.encodeWith(signatureData: Sign.SignatureData): ByteArray {
        val values = TransactionEncoder.asRlpValues(this, signatureData)
        val rlpList = RlpList(values)
        return RlpEncoder.encode(rlpList)
    }

}
