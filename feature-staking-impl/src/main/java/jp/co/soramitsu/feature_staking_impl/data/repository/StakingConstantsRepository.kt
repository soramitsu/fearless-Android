package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import java.math.BigInteger

class StakingConstantsRepository(
    private val chainRegistry: ChainRegistry,
) {

    suspend fun maxRewardedNominatorPerValidator(chainId: ChainId): Int = getNumberConstant(chainId, "MaxNominatorRewardedPerValidator").toInt()

    suspend fun lockupPeriodInEras(chainId: ChainId): BigInteger = getNumberConstant(chainId, "BondingDuration")

    suspend fun maxValidatorsPerNominator(chainId: ChainId): Int = getNumberConstant(chainId, "MaxNominations").toInt()

    private suspend fun getNumberConstant(chainId: ChainId, constantName: String): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.staking().numberConstant(constantName, runtime)
    }
}
