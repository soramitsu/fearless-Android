package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId

sealed class StakingState(val accountAddress: String) {

    class NonStash(accountAddress: String) : StakingState(accountAddress)

    sealed class Stash(
        accountAddress: String,
        val controllerId: AccountId,
        val stashId: AccountId
    ) : StakingState(accountAddress) {

        class None(
            accountAddress: String,
            controllerId: AccountId,
            stashId: AccountId
        ) : Stash(accountAddress, controllerId, stashId)

        class Validator(
            accountAddress: String,
            controllerId: AccountId,
            stashId: AccountId,
            val prefs: ValidatorPrefs
        ) : Stash(accountAddress, controllerId, stashId)

        class Nominator(
            accountAddress: String,
            controllerId: AccountId,
            stashId: AccountId,
            val nominations: Nominations
        ) : Stash(accountAddress, controllerId, stashId)
    }
}
