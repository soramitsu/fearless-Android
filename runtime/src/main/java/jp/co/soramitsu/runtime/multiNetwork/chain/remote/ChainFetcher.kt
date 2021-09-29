package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import jp.co.soramitsu.runtime.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import retrofit2.http.GET

interface ChainFetcher {

    @GET(BuildConfig.CHAINS_URL)
    suspend fun getChains(): List<ChainRemote>
}
