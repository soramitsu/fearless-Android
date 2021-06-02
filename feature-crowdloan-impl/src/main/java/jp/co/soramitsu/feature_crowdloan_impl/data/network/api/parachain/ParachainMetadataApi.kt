package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain

import retrofit2.http.GET
import retrofit2.http.Path

interface ParachainMetadataApi {

    @GET("//raw.githubusercontent.com/soramitsu/fearless-utils/master/crowdloan/{networkname}.json")
    suspend fun getParachainMetadata(
        @Path("networkname") networkName: String
    ): List<ParachainMetadataRemote>
}
