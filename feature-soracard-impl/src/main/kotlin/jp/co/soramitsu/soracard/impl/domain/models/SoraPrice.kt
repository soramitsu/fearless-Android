package jp.co.soramitsu.soracard.impl.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SoraPrice(
    val pair: String?,
    val price: String?,
    val source: String?,
    @SerialName("update_time") val updateTime: Long?
)
