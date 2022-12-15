package jp.co.soramitsu.wallet.impl.data.network.phishing

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming

interface PhishingApi {
// todo find a better place for url
    @Streaming
    @GET("https://raw.githubusercontent.com/soramitsu/fearless-utils/master/Polkadot_Hot_Wallet_Attributions.csv")
    suspend fun getPhishingAddresses(): ResponseBody
}
