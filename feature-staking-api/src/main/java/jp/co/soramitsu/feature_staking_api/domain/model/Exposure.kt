package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger

class IndividualExposure(val who: ByteArray, val value: BigInteger)

class Exposure(val total: BigInteger, val own: BigInteger, val others: List<IndividualExposure>)
