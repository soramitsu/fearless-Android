package jp.co.soramitsu.common.config

import jp.co.soramitsu.common.BuildConfig

object BuildConfigWrapper {

    val soraCardBackEndUrl: String = BuildConfig.SORACARD_BACKEND_URL.let { url ->
        if (url.endsWith("/")) url else "$url/"
    }

    val soraCardEuroRateUrl: String = soraCardBackEndUrl + "prices/xor_euro"

    val soraCardX1StatusUrl: String = soraCardBackEndUrl.replace("https", "wss", true) + "ws/x1-payment-status"
}
