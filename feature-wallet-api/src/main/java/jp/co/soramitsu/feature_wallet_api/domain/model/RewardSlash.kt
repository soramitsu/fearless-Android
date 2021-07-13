package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.feature_wallet_api.domain.model.HistoryElement

class RewardSlash(
    val amount: String,
    val isReward: Boolean,
    val era: Int,
    val validator: String
) : HistoryElement
