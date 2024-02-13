package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.common.utils.rememberAndZipAsPreviousIf
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NFTCollectionsByContractAlteringAdapter @Inject constructor ()  {

    operator fun invoke(nftCollectionResultFlow: () -> Flow<Pair<NFTCollectionResult, PaginationRequest>>): Flow<Pair<NFTCollectionResult, PaginationRequest>> =
        nftCollectionResultFlow().rememberAndZipAsPreviousIf { (collection, _) ->
            collection is NFTCollectionResult.Data.WithTokens
        }.map { (prevDataCollectionPair, currentCollectionPair) ->
            val (currentCollection, _) = currentCollectionPair

            if (currentCollection !is NFTCollectionResult.Data.WithTokens && prevDataCollectionPair != null)
                prevDataCollectionPair else currentCollectionPair
        }

}