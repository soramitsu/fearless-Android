package jp.co.soramitsu.staking.api.domain.model

import java.math.BigInteger

interface ValidatorExposure {
    val nominatorCount: Int
    val total: BigInteger
    val own: BigInteger
}

class LegacyExposure(
    override val total: BigInteger, override val own: BigInteger, val others: List<IndividualExposure>
): ValidatorExposure{
    override val nominatorCount: Int = others.size
}

class Exposure(override val total: BigInteger, override val own: BigInteger, override val nominatorCount: Int, val pageCount: BigInteger): ValidatorExposure
data class ExposurePage(val pageTotal: BigInteger, val others: List<IndividualExposure>)

class IndividualExposure(val who: ByteArray, val value: BigInteger)