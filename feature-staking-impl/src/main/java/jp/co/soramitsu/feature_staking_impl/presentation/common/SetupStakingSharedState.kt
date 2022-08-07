package jp.co.soramitsu.feature_staking_impl.presentation.common

import android.util.Log
import java.math.BigDecimal
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_api.domain.model.WithAddress
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.Sorting
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.MutableStateFlow

sealed class SetupStakingProcess {

    class Initial(val stakingType: Chain.Asset.StakingType) : SetupStakingProcess() {

        val defaultAmount = 10.toBigDecimal()

        fun fullFlow(flow: SetupStakingProcess) = flow

        fun existingStashFlow() = SelectBlockProducersStep.Validators(SelectBlockProducersStep.Payload.ExistingStash)

        fun changeValidatorsFlow() = SelectBlockProducersStep.Validators(SelectBlockProducersStep.Payload.Validators)
    }

    sealed class SetupStep : SetupStakingProcess() {
        abstract val amount: BigDecimal
        abstract fun previous(): SetupStakingProcess
        abstract fun next(
            newAmount: BigDecimal,
            rewardDestination: RewardDestination,
            currentAccountAddress: String
        ): SetupStakingProcess

        class Stash(override val amount: BigDecimal) : SetupStep() {

            override fun previous() = Initial(Chain.Asset.StakingType.RELAYCHAIN)

            override fun next(
                newAmount: BigDecimal,
                rewardDestination: RewardDestination,
                currentAccountAddress: String
            ) = SelectBlockProducersStep.Validators(SelectBlockProducersStep.Payload.Full(newAmount, rewardDestination, currentAccountAddress))
        }

        class Parachain(override val amount: BigDecimal) : SetupStep() {

            override fun previous() = Initial(Chain.Asset.StakingType.PARACHAIN)

            override fun next(
                newAmount: BigDecimal,
                rewardDestination: RewardDestination,
                currentAccountAddress: String
            ): SelectBlockProducersStep.Collators {
                val payout = RewardDestination.Payout(currentAccountAddress.fromHex())
                return SelectBlockProducersStep.Collators(SelectBlockProducersStep.Payload.Full(newAmount, payout, currentAccountAddress))
            }
        }
    }

    sealed class SelectBlockProducersStep : SetupStakingProcess() {

        abstract val filtersSet: Set<Filters>
        abstract val quickFilters: Set<Filters>
        abstract val sortingSet: Set<Sorting>

        class Collators(
            val payload: Payload.Full
        ) : SelectBlockProducersStep() {

            override val filtersSet = setOf(Filters.HavingOnChainIdentity, Filters.WithRelevantBond)
            override val quickFilters = setOf(Filters.HavingOnChainIdentity, Filters.WithRelevantBond)
            override val sortingSet =
                setOf(Sorting.EstimatedRewards, Sorting.EffectiveAmountBonded, Sorting.CollatorsOwnStake, Sorting.Delegations, Sorting.MinimumBond)

            fun next(collators: List<Collator>, selectionMethod: ReadyToSubmit.SelectionMethod): SetupStakingProcess {
                return ReadyToSubmit.Parachain(
                    ReadyToSubmit.Payload.Full(
                        payload.amount,
                        payload.rewardDestination,
                        payload.controllerAddress,
                        collators,
                        selectionMethod
                    )
                )
            }
        }

        class Validators(
            val payload: Payload
        ) : SelectBlockProducersStep() {

            override val filtersSet =
                setOf(Filters.HavingOnChainIdentity, Filters.NotSlashedFilter, Filters.NotOverSubscribed)
            override val quickFilters = setOf<Filters>()
            override val sortingSet = setOf(Sorting.EstimatedRewards, Sorting.TotalStake, Sorting.ValidatorsOwnStake)

            fun previous() = when (payload) {
                is Payload.Full -> SetupStep.Stash(payload.amount)
                else -> Initial(Chain.Asset.StakingType.RELAYCHAIN)
            }

            fun next(validators: List<Validator>, selectionMethod: ReadyToSubmit.SelectionMethod): SetupStakingProcess {
                val payload = with(payload) {
                    when (this) {
                        is Payload.Full -> ReadyToSubmit.Payload.Full(amount, rewardDestination, controllerAddress, validators, selectionMethod)
                        is Payload.ExistingStash -> ReadyToSubmit.Payload.ExistingStash(validators, selectionMethod)
                        is Payload.Validators -> ReadyToSubmit.Payload.Validators(validators, selectionMethod)
                        else -> error("Wrong payload type")
                    }
                }

                return ReadyToSubmit.Stash(payload)
            }
        }

        sealed class Payload {

            class Full(
                val amount: BigDecimal,
                val rewardDestination: RewardDestination,
                val controllerAddress: String
            ) : Payload()

            object ExistingStash : Payload()

            object Validators : Payload()

            object Collators : Payload()
        }
    }

    sealed class ReadyToSubmit<T : WithAddress>(
        val payload: Payload<T>,
    ) : SetupStakingProcess() {

        enum class SelectionMethod {
            RECOMMENDED, CUSTOM
        }

        sealed class Payload<T : WithAddress>(
            val blockProducers: List<T>,
            val selectionMethod: SelectionMethod
        ) {
            abstract fun changeBlockProducers(newBlockProducers: List<T>, selectionMethod: SelectionMethod): Payload<T>

            class Full<T : WithAddress>(
                val amount: BigDecimal,
                val rewardDestination: RewardDestination,
                val currentAccountAddress: String,
                blockProducers: List<T>,
                selectionMethod: SelectionMethod
            ) : Payload<T>(blockProducers, selectionMethod) {

                override fun changeBlockProducers(newBlockProducers: List<T>, selectionMethod: SelectionMethod): Payload<T> {
                    return Full(amount, rewardDestination, currentAccountAddress, newBlockProducers, selectionMethod)
                }
            }

            class ExistingStash(
                validators: List<Validator>,
                selectionMethod: SelectionMethod
            ) : Payload<Validator>(validators, selectionMethod) {

                override fun changeBlockProducers(newBlockProducers: List<Validator>, selectionMethod: SelectionMethod): Payload<Validator> {
                    return ExistingStash(newBlockProducers, selectionMethod)
                }
            }

            class Validators(
                validators: List<Validator>,
                selectionMethod: SelectionMethod
            ) : Payload<Validator>(validators, selectionMethod) {

                override fun changeBlockProducers(newBlockProducers: List<Validator>, selectionMethod: SelectionMethod): Payload<Validator> {
                    return Validators(newBlockProducers, selectionMethod)
                }
            }

            class Collators(
                collators: List<Collator>,
                selectionMethod: SelectionMethod
            ) : Payload<Collator>(collators, selectionMethod) {

                override fun changeBlockProducers(newBlockProducers: List<Collator>, selectionMethod: SelectionMethod): Payload<Collator> {
                    return Collators(newBlockProducers, selectionMethod)
                }
            }
        }

        class Stash(payload: Payload<Validator>) : ReadyToSubmit<Validator>(payload) {

            val filtersSet =
                setOf(Filters.HavingOnChainIdentity, Filters.NotSlashedFilter, Filters.NotOverSubscribed)
            val quickFilters = setOf<Filters>()
            val sortingSet = setOf(Sorting.EstimatedRewards, Sorting.TotalStake, Sorting.ValidatorsOwnStake)

            override fun changeBlockProducers(newBlockProducers: List<Validator>, selectionMethod: SelectionMethod): ReadyToSubmit<Validator> {
                return Stash(payload.changeBlockProducers(newBlockProducers, selectionMethod))
            }
        }

        class Parachain(payload: Payload<Collator>) : ReadyToSubmit<Collator>(payload) {
            override fun changeBlockProducers(newBlockProducers: List<Collator>, selectionMethod: SelectionMethod): ReadyToSubmit<Collator> {
                return Parachain(payload.changeBlockProducers(newBlockProducers, selectionMethod))
            }
        }

        abstract fun changeBlockProducers(newBlockProducers: List<T>, selectionMethod: SelectionMethod): ReadyToSubmit<T>

        fun previous(): SelectBlockProducersStep {

            return when (this) {
                is Stash -> {
                    val payload = when (payload) {
                        is Payload.Full -> SelectBlockProducersStep.Payload.Full(payload.amount, payload.rewardDestination, payload.currentAccountAddress)
                        is Payload.ExistingStash -> SelectBlockProducersStep.Payload.ExistingStash
                        is Payload.Validators -> SelectBlockProducersStep.Payload.Validators
                        else -> error("Wrong payload type")
                    }
                    SelectBlockProducersStep.Validators(payload)
                }
                is Parachain -> {
                    val payload = when (payload) {
//                        is Payload.Collators -> SelectBlockProducersStep.Payload.Collators //todo
                        is Payload.Full -> SelectBlockProducersStep.Payload.Full(payload.amount, payload.rewardDestination, payload.currentAccountAddress)

                        else -> error("Wrong payload type")
                    }
                    SelectBlockProducersStep.Collators(payload)
                }
            }
        }

        fun finish() = when (this) {
            is Parachain -> Initial(Chain.Asset.StakingType.PARACHAIN)
            is Stash -> Initial(Chain.Asset.StakingType.RELAYCHAIN)
        }
    }
}

class SetupStakingSharedState {

    val setupStakingProcess = MutableStateFlow<SetupStakingProcess>(SetupStakingProcess.Initial(Chain.Asset.StakingType.PARACHAIN))

    fun set(newState: SetupStakingProcess) {
        Log.d("RX", "${setupStakingProcess.value.javaClass.simpleName} -> ${newState.javaClass.simpleName}")

        setupStakingProcess.value = newState
    }

    inline fun <reified T : SetupStakingProcess> get(): T = setupStakingProcess.value as T

    inline fun <reified T : SetupStakingProcess> getOrNull(): T? = setupStakingProcess.value as? T

    fun mutate(mutation: (SetupStakingProcess) -> SetupStakingProcess) {
        set(mutation(get()))
    }
}
