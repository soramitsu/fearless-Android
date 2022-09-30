package jp.co.soramitsu.staking.impl.domain.validations.setup

import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigDecimal

class SetupStakingPayload(
    val bondAmount: BigDecimal?,
    val maxFee: BigDecimal,
    val asset: Asset,
    val controllerAddress: String,
    val isAlreadyNominating: Boolean
)
