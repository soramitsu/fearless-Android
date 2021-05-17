package jp.co.soramitsu.feature_staking_impl.domain.validations.controller

import java.math.BigDecimal

class SetControllerValidationPayload(
    val stashAddress: String,
    val controllerAddress: String,
    val fee: BigDecimal,
    val transferable: BigDecimal
)
