package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import java.math.BigInteger

private const val MAX_VALIDATORS_PER_NOMINATOR = 16

class StakingConstantsRepository(
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
) {

    suspend fun maxRewardedNominatorPerValidator(): Int = getNumberConstant("MaxNominatorRewardedPerValidator").toInt()

    suspend fun lockupPeriodInEras(): BigInteger = getNumberConstant("BondingDuration")

    fun maxValidatorsPerNominator(): Int = MAX_VALIDATORS_PER_NOMINATOR

    private suspend fun getNumberConstant(constantName: String): BigInteger {
        val runtime = runtimeProperty.get()

        return runtime.metadata.staking().numberConstant(constantName, runtime)
    }
}
