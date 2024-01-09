package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.impl.data.NftCollection
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import jp.co.soramitsu.nft.impl.data.Nft
import jp.co.soramitsu.nft.impl.domain.models.NFTTransferParams
import jp.co.soramitsu.nft.impl.presentation.filters.NftFilter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal
import java.math.BigInteger

class NftInteractor(
    val nftInteractor: NFTInteractor,
    private val sendNFTUseCase: SendNFTUseCase,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository
) {

    private val loadedCollectionsByContractAddress: MutableMap<String, NftCollection> = mutableMapOf()

    suspend fun getNfts(
        filters: List<NftFilter>,
        selectedChainId: String?,
        metaAccountId: Long
    ): Map<Chain, Result<List<NftCollection>>> {
        val result = nftInteractor.userOwnedNFTsFlow(
            paginationRequestFlow = flow { emit(PaginationRequest.NextPage) },
            chainSelectionFlow = flow { emit(selectedChainId) },
            exclusionFiltersFlow = flow { emit(filters.map { it.name.uppercase() }) }
        ).first()

        return result.groupBy { it.chainId }.mapKeys { (chainId, _) ->
            chainsRepository.getChain(chainId)
        }.mapValues { (_, collections) ->
            Result.success(
                collections.map { collection ->
                    NftCollection(
                        contractAddress = collection.contractAddress!!, // TODO remove nullability!!
                        name = collection.collectionName,
                        image = collection.imageUrl,
                        description = collection.description,
                        chainId = collection.chainId,
                        chainName = collection.chainName,
                        type = collection.type,
                        nfts = collection.tokens.map {
                             Nft(
                                 title = "${collection.collectionName} ${it.tokenId}",
                                 description = "",
                                 thumbnail = collection.imageUrl,
                                 owned = "Mine",
                                 tokenId = it.tokenId
                             )
                        },
                        collectionSize = collection.collectionSize
                    )
                }
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun accountItemsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<MyNftCollection>> {
        TODO("Please look at NFTInteractor")
    }

    data class MyNftCollection(
        val chainId: ChainId,
        val chainName: String,
        val collectionName: String,
        val description: String?,
        val imageUrl: String,
        val type: String?,
        val tokens: List<TokenBalance>,
        val collectionSize: Int
    )

    data class TokenBalance(
        val tokenId: String?,
        val balance: String?
    )

    fun getCollection(contractAddress: String): NftCollection {
        val local = loadedCollectionsByContractAddress.getOrElse(contractAddress) {
            // todo load from alchemy
            null
        }
        return local ?: throw IllegalStateException("Can't find collection with contract address: $contractAddress")
    }

    fun collectionItemsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        collectionSlugFlow: Flow<String>
    ): Flow<Result<NftCollection>> {
        TODO("Please look at NFTInteractor")
    }

    suspend fun send(
        chain: Chain,
        receiver: String,
        tokenId: String,
        tokenType: String,
    ): Result<String> {
        val sender = accountRepository.getSelectedMetaAccount().address(chain)!!

        val params = when(tokenType) {
            "ERC721" -> NFTTransferParams.ERC721(
                sender = sender,
                receiver = receiver,
                tokenId = BigInteger(tokenId.requireHexPrefix().drop(2), 16),
                data = ByteArray(0)
            )
            "ERC1155" -> NFTTransferParams.ERC1155(
                sender = sender,
                receiver = receiver,
                tokenId = tokenId.requireHexPrefix().drop(2).toBigInteger(),
                amount = BigDecimal.ONE,
                data = ByteArray(0)
            )
            else -> return Result.failure(
                IllegalArgumentException(
                    """
                        Token provided is not supported.
                    """.trimIndent()
                )
            )
        }

        return sendNFTUseCase.invoke(
            chain, params
        )
    }

    private suspend fun getAllNftChains(): List<Chain> {
        return chainsRepository.getChains()
            .filter { it.supportNft && it.alchemyNftId.isNullOrEmpty().not() }
    }
}