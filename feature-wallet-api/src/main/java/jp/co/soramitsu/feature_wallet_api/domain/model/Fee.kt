package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal

class Fee(
    val transferAmount: BigDecimal,
    val feeAmount: BigDecimal,
    val type: Chain.Asset
)
