package jp.co.soramitsu.nft.domain

import jp.co.soramitsu.core.models.ChainId

interface NftInteractor {
    suspend fun fetchNfts(chainId: ChainId)
}