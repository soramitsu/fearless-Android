package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import java.math.BigInteger

class StakingConstantsRepository(
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
) {

    suspend fun maxRewardedNominatorPerValidator(): Int = getNumberConstant("MaxNominatorRewardedPerValidator").toInt()

    suspend fun lockupPeriodInEras(): BigInteger = getNumberConstant("BondingDuration")

    suspend fun maxValidatorsPerNominator(): Int = getNumberConstant("MaxNominations").toInt()

    private suspend fun getNumberConstant(constantName: String): BigInteger {
        val runtime = runtimeProperty.get()

        return runtime.metadata.staking().numberConstant(constantName, runtime)
    }
}
