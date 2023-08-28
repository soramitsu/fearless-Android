package jp.co.soramitsu.soracard.api.presentation.models

import java.math.BigDecimal

data class SoraCardAvailabilityInfo(
    val xorBalance: BigDecimal = BigDecimal.ZERO,
    val enoughXor: Boolean = false,
    val percent: BigDecimal = BigDecimal.ZERO,
    val needInXor: String = "",
    val needInEur: String = "",
    val xorRatioAvailable: Boolean = false,
)
