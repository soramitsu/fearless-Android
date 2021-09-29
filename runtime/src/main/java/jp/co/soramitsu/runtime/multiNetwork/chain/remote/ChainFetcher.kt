package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import retrofit2.http.GET

interface ChainFetcher {

    @GET("https://raw.githubusercontent.com/soramitsu/fearless-utils/master/chains/chains_dev.json")
    suspend fun getChains(): List<ChainRemote>
}
