package jp.co.soramitsu.soracard.api.domain

import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import java.math.BigDecimal

data class SoraCardAvailabilityInfo(
    val xorBalance: BigDecimal = BigDecimal.ZERO,
    val enoughXor: Boolean = true,
    val percent: BigDecimal = BigDecimal.ZERO,
    val needInXor: String = "",
    val needInEur: String = "",
    val xorRatioAvailable: Boolean = false,
)

data class SoraCardBasicStatus(
    val initialized: Boolean,
    val initError: String?,
    val availabilityInfo: SoraCardAvailabilityInfo?,
    val verification: SoraCardCommonVerification,
    val needInstallUpdate: Boolean,
    val applicationFee: String?,
    val ibanInfo: IbanInfo?,
    val phone: String?,
)
