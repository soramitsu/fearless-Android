package jp.co.soramitsu.feature_staking_impl.domain.validations.bond

import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

class BondMoreValidationPayload(
    val stashState: StakingState.Stash,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val tokenType: Token.Type
)
