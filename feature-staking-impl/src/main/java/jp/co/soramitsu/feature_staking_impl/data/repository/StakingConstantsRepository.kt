package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import java.math.BigInteger

class StakingConstantsRepository(
    private val chainRegistry: ChainRegistry,
) {

    suspend fun maxRewardedNominatorPerValidator(chainId: ChainId): Int = getNumberConstant(chainId, "MaxNominatorRewardedPerValidator").toInt()

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

    private suspend fun getNumberConstant(chainId: ChainId, constantName: String): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.staking().numberConstant(constantName, runtime)
    }

    private suspend fun getParachainNumberConstant(chainId: ChainId, constantName: String): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.parachainStaking().numberConstant(constantName, runtime)
    }
}
