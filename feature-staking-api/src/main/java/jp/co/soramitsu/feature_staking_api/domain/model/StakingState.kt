package jp.co.soramitsu.feature_staking_api.domain.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal
import java.math.BigInteger

sealed class StakingState(
    val chain: Chain,
    val accountId: AccountId
) {

    val accountAddress: String = chain.addressOf(accountId)
    open val rewardsAddress: String = accountAddress
    open val executionAddressId: ByteArray = accountId
    val executionAddress: String
        get() = chain.addressOf(executionAddressId)

    class NonStash(chain: Chain, accountId: AccountId) : StakingState(chain, accountId)

    // for substrate staking pallet
    sealed class Stash(
        chain: Chain,
        accountId: AccountId,
        val controllerId: AccountId,
        val stashId: AccountId,
    ) : StakingState(chain, accountId) {

        val stashAddress = chain.addressOf(stashId)
        override val rewardsAddress = stashAddress
        val controllerAddress = chain.addressOf(controllerId)
        override val executionAddressId = controllerId

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

    // for parachainStaking pallet Moonbeam
    sealed class Parachain(
        chain: Chain,
        accountId: AccountId
    ) : StakingState(chain, accountId) {

        class Collator(
            chain: Chain,
            accountId: AccountId,
        ) : Parachain(chain, accountId)

        class None(
            chain: Chain,
            accountId: AccountId,
        ) : Parachain(chain, accountId)

        class Delegator(
            chain: Chain,
            accountId: AccountId,
            val delegations: List<CollatorDelegation>,
            val totalDelegatedAmount: BigDecimal,
        ) : Parachain(chain, accountId)
    }
}

data class CollatorDelegation(
    val collatorId: AccountId,
    val delegatedAmountInPlanks: BigInteger,
    val rewardedAmountInPlanks: BigInteger,
    val status: DelegatorStateStatus
)

fun DelegatorState.toDelegations(): List<CollatorDelegation> {
    return this.delegations.map {
        CollatorDelegation(
            it.owner,
            it.amount,
            BigInteger.ZERO,
            this.status
        )
    }
}
