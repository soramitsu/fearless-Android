package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

class NFTCollectionsByContractBufferingMediator @Inject constructor() {

    fun interface Callback {
        fun loadExactly(count: Int)
    }

    operator fun invoke(
        callback: Callback,
        nftCollectionFlow: () -> Flow<Pair<NFTCollectionResult, PaginationRequest>>
    ): Flow<Pair<NFTCollectionResult, PaginationRequest>> {
        return flow {
            var buffer: NFTCollectionResult.Data.WithTokens? = null

            fun updateBufferAndGetSize(newCollection: NFTCollectionResult.Data.WithTokens) =
                with(buffer) {
                    if (this == null) {
                        buffer = newCollection
                        return@with newCollection.tokens.size
                    }

                    tokens.addAll(newCollection.tokens)
                    return@with tokens.size
                }

            nftCollectionFlow().transform { collectionResult ->
                val (collection, request) = collectionResult

                if (collection !is NFTCollectionResult.Data.WithTokens)
                    return@transform emit(collectionResult)

                println("This is checkpoint: collection.tokens.size - ${collection.tokens.size}")

                if (collection.tokens.size >= 100) {
                    buffer = null
                    return@transform emit(collectionResult)
                }

                val bufferSize = buffer?.tokens?.size ?: 0

                if (bufferSize < 100) {
                    val newSize = updateBufferAndGetSize(collection)

                    println("This is checkpoint: newSize - $newSize")

                    if (newSize >= 100)
                        return@transform emit(buffer!! to request)
                    else callback.loadExactly(100 - newSize)
                } else {
                    buffer = collection
                    return@transform emit(collectionResult)
                }
            }.collect(this)
        }
    }

}