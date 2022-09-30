package jp.co.soramitsu.staking.impl.presentation.validators.details.model

import java.math.BigInteger

class NominatorModel(
    val who: ByteArray,
    val value: BigInteger
)
