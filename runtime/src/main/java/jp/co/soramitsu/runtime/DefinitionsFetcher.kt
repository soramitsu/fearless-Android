package jp.co.soramitsu.runtime

import retrofit2.http.GET
import retrofit2.http.Path

interface DefinitionsFetcher {

    @GET("https://raw.githubusercontent.com/soramitsu/fearless-utils/master/scalecodec/type_registry/{fileName}")
    suspend fun getDefinitionsByFile(@Path("fileName") fileName: String): String

    @GET("https://raw.githubusercontent.com/soramitsu/fearless-utils/crowdloands/moonbeam/scalecodec/type_registry/{fileName}")
    suspend fun getDefinitionsByFilePolkatrain(@Path("fileName") fileName: String): String
}

suspend fun DefinitionsFetcher.getDefinitionsByNetwork(networkName: String): String {
    return getDefinitionsByFile(fileName(networkName))
}

suspend fun DefinitionsFetcher.getDefinitionsPolka(networkName: String): String {
    return getDefinitionsByFilePolkatrain(fileName(networkName))
}

private fun fileName(networkName: String) = "$networkName.json"
