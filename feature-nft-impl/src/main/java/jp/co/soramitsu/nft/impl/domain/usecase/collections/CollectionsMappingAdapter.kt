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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class CollectionsMappingAdapter @Inject constructor(
    private val chainsRepository: ChainsRepository,
) {

    private val mutex: Mutex = Mutex()
    private val chainsMap: MutableMap<ChainId, Chain> = mutableMapOf()

    operator fun invoke(factory: () -> Flow<List<PagedResponse<ContractInfo>>>): Flow<Sequence<NFTCollection.Loaded>> {
        return factory().map { currentResponse ->
            val mapResults = currentResponse
                .concurrentRequestFlow { pagedResponse ->
                    emit(pagedResponse.mapToNFTCollectionResult())
                }.toList()

            return@map mapResults.asSequence().flatten()
        }.onCompletion {
            mutex.withLock { chainsMap.clear() }
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun PagedResponse<ContractInfo>.mapToNFTCollectionResult(): Sequence<NFTCollection.Loaded> {
        if (chainsMap[tag as ChainId] == null) {
            mutex.withLock {
                if (chainsMap[tag as ChainId] == null) {
                    chainsMap[tag as ChainId] = chainsRepository.getChain(tag as ChainId)
                }
            }
        }

        val (chainId, chainName) = chainsMap[tag as ChainId]!!.run { id to name }

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
