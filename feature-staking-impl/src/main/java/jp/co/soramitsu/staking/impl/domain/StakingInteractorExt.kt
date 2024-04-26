package jp.co.soramitsu.staking.impl.domain

import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure

suspend fun StakingInteractor.getSelectedChain() = selectedChainFlow().first()

fun isNominationActive(
    stashId: AccountId,
    exposures: Collection<LegacyExposure>,
    rewardedNominatorsPerValidator: Int?
): Boolean {
    val comparator = { accountId: IndividualExposure ->
        accountId.who.contentEquals(stashId)
    }

    val validatorsWithOurStake = exposures.filter { exposure ->
        exposure.others.any(comparator)
    }

    return validatorsWithOurStake.any { it.willAccountBeRewarded(stashId, rewardedNominatorsPerValidator) }
}

fun LegacyExposure.willAccountBeRewarded(
    accountId: AccountId,
    rewardedNominatorsPerValidator: Int?
): Boolean {
    if(rewardedNominatorsPerValidator == null) return true
    val indexInRewardedList = others.sortedByDescending(IndividualExposure::value).indexOfFirst {
        it.who.contentEquals(accountId)
    }

    if (indexInRewardedList == -1) {
        return false
    }

    val numberInRewardedList = indexInRewardedList + 1

    return numberInRewardedList <= rewardedNominatorsPerValidator
}

fun minimumStake(
    exposures: Collection<LegacyExposure>,
    minimumNominatorBond: BigInteger
): BigInteger {
    val stakeByNominator = exposures
        .map(LegacyExposure::others)
        .flatten()
        .fold(mutableMapOf<String, BigInteger>()) { acc, individualExposure ->
            val currentExposure = acc.getOrDefault(individualExposure.who.toHexString(), BigInteger.ZERO)

            acc[individualExposure.who.toHexString()] = currentExposure + individualExposure.value

            acc
        }

    return stakeByNominator.values.minOrZero().coerceAtLeast(minimumNominatorBond)
}

private fun Iterable<BigInteger>.minOrZero(): BigInteger = this.minOrNull() ?: BigInteger.ZERO
