package jp.co.soramitsu.wallet.impl.data.network.phishing

import retrofit2.http.GET

interface PhishingApi {
// todo find a better place for url
    @GET("https://polkadot.js.org/phishing/address.json")
    suspend fun getPhishingAddresses(): Map<String, List<String>>
}
