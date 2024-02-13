package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.nft.data.UserOwnedTokensPagedResponse
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.nft.domain.models.utils.toNFTCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

class MapUserOwnedNFTCollectionsListUseCase(
    private val nftContractsFlowFlow: () -> Flow<List<UserOwnedTokensPagedResponse>>
) {

    operator fun invoke(
    ): Flow<List<NFTCollectionResult>> {
        return nftContractsFlowFlow.invoke().map { currentResponse ->
            currentResponse.concurrentRequestFlow { pagedResponse ->
                emit(pagedResponse.mapToNFTCollectionResult())
            }.toList().flatten()
        }.flowOn(Dispatchers.Default)
    }

    private fun UserOwnedTokensPagedResponse.mapToNFTCollectionResult(): List<NFTCollectionResult> {
        val (chainId, chainName) = chain.run { id to name }

        return result.mapCatching { paginationEvent ->
            if (paginationEvent !is PaginationEvent.PageIsLoaded) {
                return@mapCatching listOf(NFTCollectionResult.Empty(chainId, chainName))
            }

            if (paginationEvent.data.contracts.isEmpty()) {
                return@mapCatching listOf(NFTCollectionResult.Empty(chainId, chainName))
            }

            paginationEvent.data.contracts.map { contract ->
                contract.toNFTCollection(
                    chainId = chainId,
                    chainName = chainName
                )
            }
        }.getOrElse { throwable ->
            val errorResult = NFTCollectionResult.Error(
                chainId = chainId,
                chainName = chainName,
                throwable = throwable
            )

            listOf(errorResult)
        }
    }


}