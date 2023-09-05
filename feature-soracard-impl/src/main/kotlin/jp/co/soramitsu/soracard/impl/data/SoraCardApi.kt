package jp.co.soramitsu.soracard.impl.data

import jp.co.soramitsu.soracard.impl.domain.models.SoraPrice
import retrofit2.http.GET
import retrofit2.http.Url

interface SoraCardApi {

    @GET
    suspend fun getXorEuroPrice(@Url url: String): SoraPrice?
}
