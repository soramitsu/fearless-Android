package jp.co.soramitsu.polkaswap.impl.domain.models

data class SwapDetails(
    val minReceived: String,
    val route: String,
    val token1ToToken2: String,
    val token2ToToken1: String,
    val networkFee: String
)
