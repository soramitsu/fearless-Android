package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.common.utils.rememberAndZipAsPreviousIf
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

class AlterNFTCollectionsResultListUseCase(
    private val nftCollectionResultFlow: () -> Flow<List<NFTCollectionResult>>
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<NFTCollectionResult>> =
        nftCollectionResultFlow().rememberAndZipAsPreviousIf { collectionsList ->
            collectionsList.any { it is NFTCollectionResult.Data }
        }.mapLatest { (prevDataCollections, currentCollections) ->
            val areCurrentDataCollectionsEmpty =
                currentCollections.count { it is NFTCollectionResult.Data } == 0

            if (areCurrentDataCollectionsEmpty && prevDataCollections != null) {
                prevDataCollections
            } else {
                currentCollections
            }
        }

}