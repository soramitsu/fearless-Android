package jp.co.soramitsu.nft.impl.presentation

import jp.co.soramitsu.nft.impl.presentation.filters.NftFilterModel
import kotlinx.coroutines.flow.Flow

interface NftRouter {
    fun openNftFilters(value: NftFilterModel)
    fun setNftFiltersResult(key: String, result: NftFilterModel, resultDestinationId: Int)
    fun nftFiltersResultFlow(key: String): Flow<NftFilterModel>
}