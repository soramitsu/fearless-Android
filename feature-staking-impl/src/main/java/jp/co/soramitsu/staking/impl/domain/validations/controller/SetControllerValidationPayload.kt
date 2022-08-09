package jp.co.soramitsu.staking.impl.domain.validations.controller

import java.math.BigDecimal

class SetControllerValidationPayload(
    val stashAddress: String,
    val controllerAddress: String,
    val fee: BigDecimal,
    val transferable: BigDecimal
)
