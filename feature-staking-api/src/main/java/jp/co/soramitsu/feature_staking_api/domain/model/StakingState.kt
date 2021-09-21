package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

sealed class StakingState(
    val chain: Chain,
    val accountId: AccountId
) {

    val accountAddress: String = chain.addressOf(accountId)

    class NonStash(chain: Chain, accountId: AccountId) : StakingState(chain, accountId)

    sealed class Stash(
        chain: Chain,
        accountId: AccountId,
        val controllerId: AccountId,
        val stashId: AccountId,
    ) : StakingState(chain, accountId) {

        val stashAddress = chain.addressOf(stashId)
        val controllerAddress = chain.addressOf(controllerId)

        class None(
            chain: Chain,
            accountId: AccountId,
            controllerId: AccountId,
            stashId: AccountId,
        ) : Stash(chain, accountId, controllerId, stashId)

        class Validator(
            chain: Chain,
            accountId: AccountId,
            controllerId: AccountId,
            stashId: AccountId,
            val prefs: ValidatorPrefs,
        ) : Stash(chain, accountId, controllerId, stashId)

        class Nominator(
            chain: Chain,
            accountId: AccountId,
            controllerId: AccountId,
            stashId: AccountId,
            val nominations: Nominations,
        ) : Stash(chain, accountId, controllerId, stashId)
    }
}
