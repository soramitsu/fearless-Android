package jp.co.soramitsu.nft.impl.domain.usecase.collections

import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.nft.impl.domain.models.nft.CollectionImpl
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
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

    operator fun invoke(factory: () -> Flow<List<PagedResponse<ContractInfo>>>): Flow<Sequence<NFTCollectionResult>> {
        return factory().map { currentResponse ->
            val mapResults = currentResponse
                .concurrentRequestFlow { pagedResponse ->
                    emit(pagedResponse.mapToNFTCollectionResult())
                }.toList()

            return@map mapResults.asSequence().flatten()
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun PagedResponse<ContractInfo>.mapToNFTCollectionResult(): Sequence<NFTCollectionResult> {
        val (chainId, chainName) = chainsRepository.getChain(tag as ChainId).run { id to name }

        val mappedSequence = result.mapCatching { pageResult ->
            if (pageResult !is PageBackStack.PageResult.ValidPage) {
                return@mapCatching sequenceOf(NFTCollectionResult.Empty(chainId, chainName))
            }

            pageResult.items.map { contract ->
                CollectionImpl(chainId, chainName, contract)
            }.ifEmpty { sequenceOf(NFTCollectionResult.Empty(chainId, chainName)) }
        }.getOrElse { throwable ->
            val errorResult = NFTCollectionResult.Error(
                chainId = chainId,
                chainName = chainName,
                throwable = throwable
            )

            sequenceOf(errorResult)
        }

        return mappedSequence
    }
}
