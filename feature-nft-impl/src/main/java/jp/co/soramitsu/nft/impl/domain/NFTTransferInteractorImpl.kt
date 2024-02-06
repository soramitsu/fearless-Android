package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.utils.subscribeNewHeads
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.domain.adapters.NFTTransferAdapter
import jp.co.soramitsu.nft.impl.domain.usecase.CreateRawEthTransaction
import jp.co.soramitsu.nft.impl.domain.usecase.EstimateEthTransactionGas
import jp.co.soramitsu.nft.impl.domain.usecase.EstimateEthTransactionNetworkFee
import jp.co.soramitsu.nft.impl.domain.usecase.SendRawEthTransaction
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transform
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

class NFTTransferInteractorImpl(
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val ethereumConnectionPool: EthereumConnectionPool
): NFTTransferInteractor {

    private fun getWeb3Connection(chainId: ChainId): EthereumWebSocketConnection {
        return ethereumConnectionPool.get(chainId) ?: error(
            """
                EthereumConnection to chain with id - $chainId - could not have been established.
            """.trimIndent()
        )
    }

    override suspend fun networkFeeFlow(
        token: NFT.Full,
        receiver: String,
        canReceiverAcceptToken: Boolean
    ): Flow<Result<BigDecimal>> {
        val chain = chainsRepository.getChain(token.chainId)
        val connection = getWeb3Connection(chain.id)

        val senderNullable = accountRepository.getSelectedMetaAccount().address(chain)

        val senderResult = runCatching {
            senderNullable ?: error(
                """
                    Currently selected account is unavailable now.
                """.trimIndent()
            )
        }

        return connection.subscribeNewHeads().transform { newHead ->
            val nftTransfer = NFTTransferAdapter(
                web3j = connection.nonNullWeb3j,
                sender = senderResult.getOrThrow(),
                receiver = receiver,
                token = token,
                canReceiverAcceptToken = canReceiverAcceptToken
            )

            val networkFee = connection.EstimateEthTransactionNetworkFee(
                call = nftTransfer,
                baseFeePerGas = Numeric.decodeQuantity(newHead.params.result?.baseFeePerGas)
            )

            emit(Result.success(networkFee))
        }.catch { emit(Result.failure(it)) }
    }

    override suspend fun isReceiverAddressCorrect(
        chainId: ChainId,
        receiver: String
    ): Result<Boolean> {
        val chain = chainsRepository.getChain(chainId)

        return runCatching { chain.isValidAddress(receiver) }
    }

    override suspend fun send(
        token: NFT.Full,
        receiver: String,
        canReceiverAcceptToken: Boolean
    ): Result<String> {
        val chain = chainsRepository.getChain(token.chainId)
        val connection = getWeb3Connection(chain.id)

        return runCatching {
            val chainId = chain.id.requireHexPrefix().drop(2).toLong()

            val ethereumSecrets =
                accountRepository.getMetaAccountSecrets(
                    metaId = accountRepository.getSelectedMetaAccount().id
                )?.get(MetaAccountSecrets.EthereumKeypair) ?: error(
                    """
                        Public or Private key is not available.
                    """.trimIndent()
                )

            val keypair = with(ethereumSecrets) {
                Keypair(
                    publicKey = get(KeyPairSchema.PublicKey),
                    privateKey = get(KeyPairSchema.PrivateKey)
                )
            }

            val sender = accountRepository.getSelectedMetaAccount().address(chain) ?: error(
                """
                    Currently selected account is unavailable now.
                """.trimIndent()
            )

            val nftTransfer = NFTTransferAdapter(
                web3j = connection.nonNullWeb3j,
                sender = sender,
                receiver = receiver,
                token = token,
                canReceiverAcceptToken = canReceiverAcceptToken
            )

            return@runCatching connection.SendRawEthTransaction(
                keypair = keypair,
                ethereumChainId = chainId,
                transaction = connection.CreateRawEthTransaction(
                    call = nftTransfer
                )
            )
        }
    }
}