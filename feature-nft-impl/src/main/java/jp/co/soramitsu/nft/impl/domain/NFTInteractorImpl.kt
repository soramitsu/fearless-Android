package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.nft.impl.domain.models.nft.NFTImpl
import jp.co.soramitsu.nft.impl.domain.usecase.collections.CollectionsFetchingUseCase
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.TokensFetchingUseCase
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

class NFTInteractorImpl(
    private val accountRepository: AccountRepository,
    private val nftRepository: NFTRepository,
    private val chainsRepository: ChainsRepository,
    private val collectionsFetchingUseCase: CollectionsFetchingUseCase,
    private val tokensFetchingUseCase: TokensFetchingUseCase
) : NFTInteractor {

    override fun setNFTFilter(filter: NFTFilter, isApplied: Boolean) {
        nftRepository.setNFTFilter(filter.name, !isApplied)
    }

    override fun nftFiltersFlow(): Flow<Map<NFTFilter, Boolean>> {
        return nftRepository.nftFiltersFlow.map { allCurrentlyAppliedFilters ->
            NFTFilter.values().associateWith { filter ->
                filter.name !in allCurrentlyAppliedFilters
            }
        }
    }

    override fun collectionsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<Sequence<NFTCollection>> = collectionsFetchingUseCase(
        paginationRequestFlow = paginationRequestFlow,
        chainSelectionFlow = chainSelectionFlow
    )

    override fun tokensFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<NFTCollection> = tokensFetchingUseCase(
        paginationRequestFlow = paginationRequestFlow,
        chainSelectionFlow = chainSelectionFlow,
        contractAddressFlow = contractAddressFlow
    )

    override suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): Result<NFT> {
        val chain = chainsRepository.getChain(chainId)

        return nftRepository.tokenMetadata(chain, contractAddress, tokenId).map { result ->
            NFTImpl(result, chain.id, chain.name)
        }
    }

    override suspend fun getOwnersForNFT(token: NFT): Result<List<String>?> {
        val chain = chainsRepository.getChain(token.chainId)
        val selectedAccount = accountRepository.getSelectedMetaAccount()

        return runCatching {
            val accountAddress = selectedAccount.address(chain)

            require(token.contractAddress.isNotBlank()) {
                """
                    TokenId supplied is null.
                """.trimIndent()
            }

            require(token.tokenId >= BigInteger.ZERO) {
                """
                    TokenId supplied is null.
                """.trimIndent()
            }

            return@runCatching nftRepository.tokenOwners(
                chain = chain,
                contractAddress = token.contractAddress,
                tokenId = token.tokenId.toString()
            ).map { response ->
                when {
                    response.ownersList.isEmpty() && token.isUserOwnedToken ->
                        accountAddress?.let { listOf(it) }

                    response.ownersList.isEmpty() && !token.isUserOwnedToken ->
                        emptyList()

                    response.ownersList.isNotEmpty() && token.isUserOwnedToken ->
                        buildList {
                            accountAddress?.let { add(it) }

                            response.ownersList.filter { it != accountAddress }
                                .toCollection(this)
                        }

                    response.ownersList.isNotEmpty() && !token.isUserOwnedToken ->
                        response.ownersList

                    else -> null
                }?.map { it.requireHexPrefix() }
            }.getOrThrow()
        }
    }
}
