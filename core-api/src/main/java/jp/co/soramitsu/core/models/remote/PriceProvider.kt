package jp.co.soramitsu.core.models.remote

data class PriceProvider(
    val id: String,
    val type: String,
    val precision: Int = 0
)
