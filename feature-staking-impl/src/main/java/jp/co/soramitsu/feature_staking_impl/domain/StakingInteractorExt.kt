package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import kotlinx.coroutines.flow.first
import java.math.BigInteger

suspend fun StakingInteractor.getSelectedChain() = selectedChainFlow().first()

fun isNominationActive(
    stashId: AccountId,
    exposures: Collection<Exposure>,
    rewardedNominatorsPerValidator: Int
): Boolean {
    val comparator = { accountId: IndividualExposure ->
        accountId.who.contentEquals(stashId)
    }

    val validatorsWithOurStake = exposures.filter { exposure ->
        exposure.others.any(comparator)
    }

    return validatorsWithOurStake.any { it.willAccountBeRewarded(stashId, rewardedNominatorsPerValidator) }
}

fun Exposure.willAccountBeRewarded(
    accountId: AccountId,
    rewardedNominatorsPerValidator: Int
): Boolean {
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
    exposures: Collection<Exposure>,
    minimumNominatorBond: BigInteger,
): BigInteger {

    val stakeByNominator = exposures
        .map(Exposure::others)
        .flatten()
        .fold(mutableMapOf<String, BigInteger>()) { acc, individualExposure ->
            val currentExposure = acc.getOrDefault(individualExposure.who.toHexString(), BigInteger.ZERO)

            acc[individualExposure.who.toHexString()] = currentExposure + individualExposure.value

            acc
        }

    return stakeByNominator.values.minOrZero().coerceAtLeast(minimumNominatorBond)
}

private fun Iterable<BigInteger>.minOrZero(): BigInteger = this.minOrNull() ?: BigInteger.ZERO
