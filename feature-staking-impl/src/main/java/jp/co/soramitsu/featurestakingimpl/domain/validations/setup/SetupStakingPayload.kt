package jp.co.soramitsu.featurestakingimpl.domain.validations.setup

import jp.co.soramitsu.featurewalletapi.domain.model.Asset
import java.math.BigDecimal

class SetupStakingPayload(
    val bondAmount: BigDecimal?,
    val maxFee: BigDecimal,
    val asset: Asset,
    val controllerAddress: String,
    val isAlreadyNominating: Boolean
)
