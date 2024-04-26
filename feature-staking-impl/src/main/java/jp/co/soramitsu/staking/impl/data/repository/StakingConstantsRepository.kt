package jp.co.soramitsu.staking.impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.common.utils.constantOrNull
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.common.utils.stakingOrNull
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId

class StakingConstantsRepository(
    private val chainRegistry: ChainRegistry
) {

    // returns null if there are infinity nominators per validator
    suspend fun maxRewardedNominatorPerValidator(chainId: ChainId): Int? {
        return try {
            val runtime = chainRegistry.getRuntime(chainId)

            if(runtime.metadata.stakingOrNull()?.constantOrNull("MaxNominatorRewardedPerValidator") != null){
                return getNumberConstant(chainId, "MaxNominatorRewardedPerValidator").toInt()
            } else {
                // todo need research
                //getNumberConstant(chainId, "MaxExposurePageSize").toInt()
                return null
            }
        } catch (e: NoSuchElementException) {
            when (chainId) {
                westendChainId, polkadotChainId, kusamaChainId -> null
                else -> throw e
            }
        }
    }

    suspend fun lockupPeriodInEras(chainId: ChainId): BigInteger = getNumberConstant(chainId, "BondingDuration")

    suspend fun parachainLockupPeriodInRounds(chainId: ChainId): BigInteger = getParachainNumberConstant(chainId, "RevokeDelegationDelay")

    suspend fun parachainLeaveCandidatesDelay(chainId: ChainId): BigInteger = getParachainNumberConstant(chainId, "LeaveCandidatesDelay")

    suspend fun parachainMinimumStaking(chainId: ChainId): BigInteger = getParachainNumberConstant(chainId, "MinDelegation")

    suspend fun maxValidatorsPerNominator(chainId: ChainId): Int {
        return try {
            getNumberConstant(chainId, "MaxNominations").toInt()
        } catch (e: NoSuchElementException) {
            when (chainId) {
                kusamaChainId -> 24
                polkadotChainId, westendChainId -> 16
                else -> throw e
            }
        }
    }

    suspend fun maxDelegationsPerDelegator(chainId: ChainId): Int {
        return getParachainNumberConstant(chainId, "MaxDelegationsPerDelegator").toInt()
    }

    suspend fun maxTopDelegationsPerCandidate(chainId: ChainId): Int {
        return getParachainNumberConstant(chainId, "MaxTopDelegationsPerCandidate").toInt()
    }

    suspend fun maxBottomDelegationsPerCandidate(chainId: ChainId): Int {
        return getParachainNumberConstant(chainId, "MaxBottomDelegationsPerCandidate").toInt()
    }

    suspend fun candidateBondLessDelay(chainId: ChainId): Int {
        return getParachainNumberConstant(chainId, "CandidateBondLessDelay").toInt()
    }

    private suspend fun getNumberConstant(chainId: ChainId, constantName: String): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.staking().numberConstant(constantName, runtime)
    }

    private suspend fun getParachainNumberConstant(chainId: ChainId, constantName: String): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.parachainStaking().numberConstant(constantName, runtime)
    }
}
