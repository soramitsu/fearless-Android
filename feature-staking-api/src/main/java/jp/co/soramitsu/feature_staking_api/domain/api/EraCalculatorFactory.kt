package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.feature_staking_api.domain.model.EraIndex
import java.math.BigInteger

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
    private val sessionLength: BigInteger,
    private val eraLength: BigInteger,
    private val blockCreationTime: BigInteger,
    private val currentSessionIndex: BigInteger,
    private val currentSlot: BigInteger,
    private val genesisSlot: BigInteger,
    private val eraStartSessionIndex: BigInteger,
    private val currentEra: EraIndex,
) {
    fun calculate(destinationEra: EraIndex? = null): BigInteger {
        val sessionStartSlot = currentSessionIndex * sessionLength + genesisSlot
        val sessionProgress = currentSlot - sessionStartSlot
        val eraProgress = (currentSessionIndex - eraStartSessionIndex) * sessionLength + sessionProgress
        val eraRemained = eraLength * sessionLength - eraProgress

        return if (destinationEra != null) {
            val leftEras = destinationEra - currentEra - 1.toBigInteger()
            val timeForLeftEras = leftEras * eraLength * sessionLength * blockCreationTime
            eraRemained * blockCreationTime + timeForLeftEras
        } else {
            eraRemained * blockCreationTime
        }
    }
}

class EraTimeCalculatorFactory(val repository: StakingRepository) {
    suspend fun create(): EraTimeCalculator {
        val sessionLength = repository.sessionLength()
        val eraLength = repository.eraLength()
        val blockCreationTime = repository.blockCreationTime()
        val currentSessionIndex = repository.currentSessionIndex()
        val currentSlot = repository.currentSlot()
        val genesisSlot = repository.genesisSlot()
        val currentEra = repository.getCurrentEraIndex()
        val eraStartSessionIndex = repository.eraStartSessionIndex(currentEra)

        return EraTimeCalculator(
            sessionLength,
            eraLength,
            blockCreationTime,
            currentSessionIndex,
            currentSlot,
            genesisSlot,
            eraStartSessionIndex,
            currentEra
        )
    }
}
