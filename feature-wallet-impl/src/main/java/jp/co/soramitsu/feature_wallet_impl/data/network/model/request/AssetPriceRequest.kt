package jp.co.soramitsu.feature_wallet_impl.data.network.model.request

import java.util.concurrent.TimeUnit

class AssetPriceRequest(val time: Long) {
    companion object {
        fun createForNow() = AssetPriceRequest(toSeconds(System.currentTimeMillis()))

        fun createForYesterday(): AssetPriceRequest {
            val time = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

            return AssetPriceRequest(toSeconds(time))
        }

        private fun toSeconds(millis: Long) = TimeUnit.MILLISECONDS.toSeconds(millis)
    }
}