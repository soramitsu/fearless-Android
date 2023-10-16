package jp.co.soramitsu.nft.impl.data

import jp.co.soramitsu.feature_nft_impl.BuildConfig
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId

class NftRepository(private val alchemyNftApi: AlchemyNftApi) {

    suspend fun getNfts(chain: Chain, address: String) {
        val response = alchemyNftApi.getNfts(url = chain.getUrl(), owner = address)
        hashCode()
    }

    private fun Chain.getUrl(): String {
        return "https://${alchemyNftId}.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getNFTs"
    }
}