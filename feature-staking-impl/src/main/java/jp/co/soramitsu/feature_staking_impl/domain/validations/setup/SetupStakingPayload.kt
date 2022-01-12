package jp.co.soramitsu.feature_staking_impl.domain.validations.setup

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import java.math.BigDecimal

class SetupStakingPayload(
    val bondAmount: BigDecimal?,
    val maxFee: BigDecimal,
    val asset: Asset,
    val controllerAddress: String,
    val isAlreadyNominating: Boolean
)
