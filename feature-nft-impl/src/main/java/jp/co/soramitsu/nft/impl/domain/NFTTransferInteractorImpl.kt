package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.domain.usecase.eth.CreateRawEthTransaction
import jp.co.soramitsu.nft.impl.domain.usecase.eth.ExecuteEthFunction
import jp.co.soramitsu.nft.impl.domain.usecase.eth.SendRawEthTransaction
import jp.co.soramitsu.nft.impl.domain.usecase.eth.estimateEthTransactionNetworkFee
import jp.co.soramitsu.nft.impl.domain.usecase.transfer.NFTAccountBalanceAdapter
import jp.co.soramitsu.nft.impl.domain.usecase.transfer.NFTTransferAdapter
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.wallet.impl.data.network.blockchain.subscribeBaseFeePerGas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.coroutines.CoroutineContext

class NFTTransferInteractorImpl(
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val ethereumConnectionPool: EthereumConnectionPool,
    private val coroutineContext: CoroutineContext = Dispatchers.Default
) : NFTTransferInteractor {

    private fun getWeb3Connection(chainId: ChainId): EthereumChainConnection {
        return ethereumConnectionPool.getOrNull(chainId) ?: error(
            """
                EthereumConnection to chain with id - $chainId - could not have been established.
            """.trimIndent()
        )
    }

    override suspend fun networkFeeFlow(
        token: NFT,
        receiver: String,
        canReceiverAcceptToken: Boolean
    ): Flow<Result<BigDecimal>> = withContext(coroutineContext) {
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
        return@withContext connection.subscribeBaseFeePerGas().filter { it != null }.transform { baseFeePerGas ->
            val nftTransfer = NFTTransferAdapter(
                web3j = connection.nonNullWeb3j,
                sender = senderResult.getOrThrow(),
                receiver = receiver,
                token = token,
                canReceiverAcceptToken = canReceiverAcceptToken
            )

            val networkFee = connection.estimateEthTransactionNetworkFee(
                call = nftTransfer,
                baseFeePerGas = baseFeePerGas ?: return@transform
            )
            emit(Result.success(networkFee))
        }.catch {
            emit(Result.failure(it))
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun send(
        token: NFT,
        receiver: String,
        canReceiverAcceptToken: Boolean
    ): Result<String> = withContext(coroutineContext) {
        val chain = chainsRepository.getChain(token.chainId)
        val connection = getWeb3Connection(chain.id)

        runCatching {
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
                transaction = connection.CreateRawEthTransaction(
                    call = nftTransfer
                )
            )
        }
    }

    override suspend fun balance(token: NFT): Result<BigInteger> = withContext(coroutineContext) {
        val chain = chainsRepository.getChain(token.chainId)
        val connection = getWeb3Connection(chain.id)

        runCatching {
            val sender = accountRepository.getSelectedMetaAccount().address(chain) ?: error(
                """
                    Currently selected account is unavailable now.
                """.trimIndent()
            )

            val nftTransfer = NFTAccountBalanceAdapter(
                sender = sender,
                token = token
            )

            return@runCatching connection.ExecuteEthFunction(
                call = nftTransfer
            ) { Numeric.decodeQuantity(it?.value!!.toString()) }
        }
    }
}
