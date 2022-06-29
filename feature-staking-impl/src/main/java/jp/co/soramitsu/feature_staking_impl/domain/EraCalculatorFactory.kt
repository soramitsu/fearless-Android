package jp.co.soramitsu.feature_staking_impl.domain

import java.math.BigInteger
import jp.co.soramitsu.feature_staking_api.domain.model.EraIndex
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

/**
 * Estimating era completion time
In the project it is important to calculate estimated time of era completion for the following reasons:

- To explain a user when the stake will start working and return reward;
- To explain a user when election period ends and staking update/setup will be available;
- We should understand the difference between session slot and block. There are a fixed number of slots per session.
For each session a validator is assigned but the validator might not produce a block.
So there might be cases when blockchain receives less blocks than slots.

The steps are the following:

- Determine number of sessions per era eraLength (constant Staking.SessionsPerEra)
- Determine number of slots per session sessionLength (constant Babe.EpochDuration)
- Fetch eraStartSessionIndex (Staking.ErasStartSessionIndex passing active era);
- Fetch currentSessionIndex (Session.CurrentIndex)
- Difference between current index and start index multiplied by epoch duration gives us progress in slots for active era but we are missing progress inside session.
- To estimate progress of the session one needs: currentSlot (storage Babe.CurrentSlot) and  genesisSlot (Babe.GenesisSlot).
- Calculate sessionStartSlot = currentSessionIndex * sessionLength + genesisSlot
- Calculate sessionProgress = currentSlot - sessionStartSlot
- Calculate eraProgress = (currentSessionIndex - eraStartSessionIndex) * sessionLength + sessionProgress
- Calculate eraRemained = eraLength * sessionLength - eraProgress
- Fetch block creation time (constant Babe.ExpectedBlockTime)
- Multiplying eraRemained to expected block time gives us an estimate of era completion in milliseconds;
 */

class EraTimeCalculator(
    private val startTimeStamp: BigInteger,
    private val sessionLength: BigInteger, // Number of blocks per session
    private val eraLength: BigInteger, // Number of sessions per era
    private val blockCreationTime: BigInteger, // How long it takes to create a block
    private val currentSessionIndex: BigInteger,
    private val currentSlot: BigInteger,
    private val genesisSlot: BigInteger,
    private val eraStartSessionIndex: BigInteger,
    private val activeEra: EraIndex,
) {
    fun calculate(destinationEra: EraIndex? = null): BigInteger {
        val sessionStartSlot = currentSessionIndex * sessionLength + genesisSlot
        val sessionProgress = currentSlot - sessionStartSlot
        val eraProgress = (currentSessionIndex - eraStartSessionIndex) * sessionLength + sessionProgress
        val eraRemained = eraLength * sessionLength - eraProgress

        val finishTimeStamp = System.currentTimeMillis().toBigInteger()
        // Doing math takes very long time. By finishing all requests and calculations the time will be outdated for ~5 seconds
        val deltaTime = finishTimeStamp - startTimeStamp

        return if (destinationEra != null) {
            val leftEras = destinationEra - activeEra - 1.toBigInteger()
            val timeForLeftEras = leftEras * eraLength * sessionLength * blockCreationTime

            eraRemained * blockCreationTime + timeForLeftEras - deltaTime
        } else {
            eraRemained * blockCreationTime - deltaTime
        }
    }

    fun calculateTillEraSet(destinationEra: EraIndex): BigInteger {
        val sessionDuration = sessionLength * blockCreationTime
        val tillEraStart = calculate(destinationEra)
        return tillEraStart - sessionDuration
    }
}

class EraTimeCalculatorFactory(val repository: StakingRelayChainScenarioRepository) {
    suspend fun create(chainId: ChainId): EraTimeCalculator {
        val startRequestTime = System.currentTimeMillis().toBigInteger()
        val sessionLength = repository.sessionLength(chainId)
        val eraLength = repository.eraLength(chainId)
        val blockCreationTime = repository.blockCreationTime(chainId)
        val currentSessionIndex = repository.currentSessionIndex(chainId)
        val currentSlot = repository.currentSlot(chainId)
        val genesisSlot = repository.genesisSlot(chainId)
        val activeEra = repository.getActiveEraIndex(chainId)
        val eraStartSessionIndex = repository.eraStartSessionIndex(chainId, activeEra)

        return EraTimeCalculator(
            startRequestTime,
            sessionLength,
            eraLength,
            blockCreationTime,
            currentSessionIndex,
            currentSlot,
            genesisSlot,
            eraStartSessionIndex,
            activeEra
        )
    }
}
