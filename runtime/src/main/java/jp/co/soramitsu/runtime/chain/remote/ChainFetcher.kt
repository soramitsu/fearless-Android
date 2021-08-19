package jp.co.soramitsu.runtime.chain.remote

import jp.co.soramitsu.runtime.chain.remote.model.ChainRemote
import retrofit2.http.GET

interface ChainFetcher {

    @GET("https://raw.githubusercontent.com/soramitsu/fearless-utils/master/chains/chains.json")
    suspend fun getChains(): List<ChainRemote>
}
