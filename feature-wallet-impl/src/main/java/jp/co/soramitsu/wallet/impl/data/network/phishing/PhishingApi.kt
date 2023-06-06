package jp.co.soramitsu.wallet.impl.data.network.phishing

import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming

interface PhishingApi {

    @Streaming
    @GET(BuildConfig.SCAM_DETECTION_CONFIG)
    suspend fun getPhishingAddresses(): ResponseBody
}
