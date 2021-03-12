package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model

import java.math.BigInteger

class NominatorModel(
    val who: ByteArray,
    val value: BigInteger
)
