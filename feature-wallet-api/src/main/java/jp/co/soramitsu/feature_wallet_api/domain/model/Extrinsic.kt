package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

class Extrinsic(
    val hash: String,
    val module: String,
    val call: String,
    val fee: String,
    val success: Boolean
) : HistoryElement
