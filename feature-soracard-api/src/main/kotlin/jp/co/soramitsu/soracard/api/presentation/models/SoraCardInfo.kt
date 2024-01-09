package jp.co.soramitsu.soracard.api.presentation.models

data class SoraCardInfo(
    val id: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpirationTime: Long,
    val kycStatus: String
)
