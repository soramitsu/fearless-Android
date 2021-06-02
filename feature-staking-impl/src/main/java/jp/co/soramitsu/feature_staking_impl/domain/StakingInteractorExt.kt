package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure

fun isNominationActive(
    stashId: ByteArray,
    exposures: Collection<Exposure>,
    rewardedNominatorsPerValidator: Int
): Boolean {
    val comparator = { accountId: IndividualExposure ->
        accountId.who.contentEquals(stashId)
    }

    val validatorsWithOurStake = exposures.filter { exposure ->
        exposure.others.any(comparator)
    }

    return validatorsWithOurStake.any {
        val numberInRewardedList = it.others.sortedByDescending(IndividualExposure::value).indexOfFirst(comparator) + 1

        numberInRewardedList <= rewardedNominatorsPerValidator
    }
}
