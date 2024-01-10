package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.NFTTransferParams
import jp.co.soramitsu.nft.impl.domain.usecase.CreateRawEthTransactionUseCase
import jp.co.soramitsu.nft.impl.domain.usecase.EstimateNFTTransactionNetworkFeeUseCase
import jp.co.soramitsu.nft.impl.domain.usecase.NFTTransferFunctionAdapter
import jp.co.soramitsu.nft.impl.domain.usecase.SendRawEthTransactionUseCase
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.nft.impl.domain.utils.subscribeNewHeads
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.math.BigDecimal

class NFTTransferInteractorImpl(
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val ethereumConnectionPool: EthereumConnectionPool,
    private val nftTransferFunctionAdapter: NFTTransferFunctionAdapter,
    private val estimateNFTTransactionNetworkFeeUseCase: EstimateNFTTransactionNetworkFeeUseCase,
    private val createRawEthTransactionUseCase: CreateRawEthTransactionUseCase,
    private val sendRawEthTransactionUseCase: SendRawEthTransactionUseCase,
): NFTTransferInteractor {

    private fun getWeb3Connection(chainId: ChainId): EthereumWebSocketConnection {
        return ethereumConnectionPool.get(chainId) ?: error(
            """
                EthereumConnection to chain with id - $chainId - could not have been established.
            """.trimIndent()
        )
    }

    override suspend fun networkFeeFlow(
        token: NFTCollection.NFT.Full,
        receiver: String,
        erc1155TransferAmount: BigDecimal?
    ): Flow<BigDecimal> {
        val chain = chainsRepository.getChain(token.chainId)
        val connection = getWeb3Connection(chain.id)
        val sender = accountRepository.getSelectedMetaAccount().address(chain) ?: error(
            """
                Currently selected account is unavailable now.
            """.trimIndent()
        )

        val nftTransferParams = NFTTransferParams.create(
            sender = sender,
            receiver = receiver,
            token = token,
            erc1155TransferAmount = erc1155TransferAmount
        )

        return connection.subscribeNewHeads()
            .transform { newHead ->
                val networkFee = estimateNFTTransactionNetworkFeeUseCase(
                    socketConnection = connection,
                    chain = chain,
                    params = nftTransferParams,
                    nonce = newHead.params.result?.nonce,
                    baseFeePerGas = newHead.params.result?.baseFeePerGas
                )

                emit(networkFee)
            }
    }

    override suspend fun send(
        token: NFTCollection.NFT.Full,
        receiver: String,
        erc1155TransferAmount: BigDecimal?
    ): Result<String> {
        val chain = chainsRepository.getChain(token.chainId)
        val web3j = getWeb3Connection(chain.id).nonNullWeb3j

        return runCatching {
            val sender = accountRepository.getSelectedMetaAccount().address(chain) ?: error(
                """
                    Currently selected account is unavailable now.
                """.trimIndent()
            )

            val nftTransferParams = NFTTransferParams.create(
                sender = sender,
                receiver = receiver,
                token = token,
                erc1155TransferAmount = erc1155TransferAmount
            )

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

            val rawTransaction = createRawEthTransactionUseCase(
                web3j = web3j,
                fromAddress = nftTransferParams.sender,
                toAddress = nftTransferParams.receiver,
                data = nftTransferFunctionAdapter(nftTransferParams),
                value = null,
                nonce = null,
                gasLimit = null,
                gasPrice = null
            )

            return@runCatching sendRawEthTransactionUseCase(
                web3j = web3j,
                keypair = keypair,
                transaction = rawTransaction,
                ethereumChainId = chain.id.toLong()
            )
        }
    }
}