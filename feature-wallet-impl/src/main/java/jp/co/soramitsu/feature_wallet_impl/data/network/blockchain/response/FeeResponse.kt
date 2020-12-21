package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response

import java.math.BigInteger

class FeeResponse(
    val partialFee: BigInteger,
    val weight: Long
)