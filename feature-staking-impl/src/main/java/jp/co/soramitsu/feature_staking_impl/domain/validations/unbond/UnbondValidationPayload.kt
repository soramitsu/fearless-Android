package jp.co.soramitsu.feature_staking_impl.domain.validations.unbond

import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

data class UnbondValidationPayload(
    val stash: StakingState.Stash,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val asset: Asset,
    val tokenType: Token.Type,
)
