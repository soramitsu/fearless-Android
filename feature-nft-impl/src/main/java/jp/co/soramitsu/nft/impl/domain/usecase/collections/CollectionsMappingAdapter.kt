package jp.co.soramitsu.nft.impl.domain.usecase.collections

import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.nft.CollectionImpl
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

class CollectionsMappingAdapter @Inject constructor(
    private val chainsRepository: ChainsRepository,
) {

    operator fun invoke(factory: () -> Flow<List<PagedResponse<ContractInfo>>>): Flow<Sequence<NFTCollection.Loaded>> {
        return factory().map { currentResponse ->
            // One catalog snapshot per batch, read without waiting. A chain that left the catalog after its page
            // was requested drops that page. 4.3.0-4.3.7 used the strict 60-second getChain here, and its
            // timeout crashed the wallet home.
            val chainsById = if (currentResponse.isEmpty()) emptyMap() else chainsRepository.getChainsById()
            val mapResults = currentResponse
                .concurrentRequestFlow { pagedResponse ->
                    emit(pagedResponse.mapToNFTCollectionResult(chainsById))
                }.toList()

            return@map mapResults.asSequence().flatten()
        }.flowOn(Dispatchers.Default)
    }

    private fun PagedResponse<ContractInfo>.mapToNFTCollectionResult(
        chainsById: Map<ChainId, Chain>
    ): Sequence<NFTCollection.Loaded> {
        val chain = chainsById[tag as ChainId] ?: return emptySequence()
        val (chainId, chainName) = chain.id to chain.name

        val mappedSequence = result.mapCatching { pageResult ->
            pageResult.items.map { contract ->
                CollectionImpl(chainId, chainName, contract)
            }.ifEmpty { sequenceOf(NFTCollection.Loaded.Result.Empty(chainId, chainName)) }
        }.getOrElse { throwable ->
            val errorResult = NFTCollection.Loaded.WithFailure(
                chainId = chainId,
                chainName = chainName,
                throwable = throwable
            )

            sequenceOf(errorResult)
        }

        return mappedSequence
    }
}
