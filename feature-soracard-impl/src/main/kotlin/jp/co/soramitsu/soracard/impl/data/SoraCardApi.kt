package jp.co.soramitsu.soracard.impl.data

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.soracard.impl.domain.models.SoraPrice
import retrofit2.http.GET

interface SoraCardApi {

    @GET(BuildConfig.SORA_XOR_EURO_URL)
    suspend fun getXorEuroPrice(): SoraPrice?
}
