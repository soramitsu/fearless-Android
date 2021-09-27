package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface ParachainMetadataApi {

    @GET()
    suspend fun getParachainMetadata(
        @Url url: String
    ): List<ParachainMetadataRemote>
}
