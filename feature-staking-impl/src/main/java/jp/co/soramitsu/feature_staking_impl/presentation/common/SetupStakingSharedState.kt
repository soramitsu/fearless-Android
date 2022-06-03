package jp.co.soramitsu.feature_staking_impl.presentation.common

import android.util.Log
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

sealed class SetupStakingProcess {

    object Initial : SetupStakingProcess() {

        val defaultAmount = 10.toBigDecimal()

        fun fullFlow(flow: SetupStakingProcess) = flow

        fun existingStashFlow() = Validators(Validators.Payload.ExistingStash)

        fun changeValidatorsFlow() = Validators(Validators.Payload.Validators)
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

            override fun previous() = Initial

            override fun next(
                newAmount: BigDecimal,
                rewardDestination: RewardDestination,
                currentAccountAddress: String
            ) = Validators(Validators.Payload.Full(newAmount, rewardDestination, currentAccountAddress))
        }

        class Parachain(override val amount: BigDecimal) : SetupStep() {

            override fun previous() = Initial

            override fun next(
                newAmount: BigDecimal,
                rewardDestination: RewardDestination,
                currentAccountAddress: String
            ) = Validators(Validators.Payload.Full(newAmount, rewardDestination, currentAccountAddress))
        }
    }

    class Collators(
        val payload: Validators.Payload
    ) : SetupStakingProcess() {
        fun next(validators: List<Validator>, collators: List<Collator>, selectionMethod: ReadyToSubmit.SelectionMethod): SetupStakingProcess {
            val payload = with(payload) {
                when (this) {
                    is Validators.Payload.Full ->
                        ReadyToSubmit.Payload.Full(amount, rewardDestination, controllerAddress, validators, collators, selectionMethod)
                    is Validators.Payload.ExistingStash ->
                        ReadyToSubmit.Payload.ExistingStash(validators, collators, selectionMethod)
                    is Validators.Payload.Validators ->
                        ReadyToSubmit.Payload.Validators(validators, collators, selectionMethod)
                }
            }

            return ReadyToSubmit(payload)
        }
    }

    class Validators(
        val payload: Payload
    ) : SetupStakingProcess() {

        sealed class Payload {

            class Full(
                val amount: BigDecimal,
                val rewardDestination: RewardDestination,
                val controllerAddress: String
            ) : Payload()

            object ExistingStash : Payload()

            object Validators : Payload()
        }

        fun previous() = when (payload) {
            is Payload.Full -> SetupStep.Stash(payload.amount)
            else -> Initial
        }

        fun next(validators: List<Validator>, collators: List<Collator>, selectionMethod: ReadyToSubmit.SelectionMethod): SetupStakingProcess {
            val payload = with(payload) {
                when (this) {
                    is Payload.Full -> ReadyToSubmit.Payload.Full(amount, rewardDestination, controllerAddress, validators, collators, selectionMethod)
                    is Payload.ExistingStash -> ReadyToSubmit.Payload.ExistingStash(validators, collators, selectionMethod)
                    is Payload.Validators -> ReadyToSubmit.Payload.Validators(validators, collators, selectionMethod)
                }
            }

            return ReadyToSubmit(payload)
        }
    }

    class ReadyToSubmit(
        val payload: Payload,
    ) : SetupStakingProcess() {

        enum class SelectionMethod {
            RECOMMENDED, CUSTOM
        }

        sealed class Payload(
            val validators: List<Validator>,
            val collators: List<Collator>,
            val selectionMethod: SelectionMethod
        ) {

            class Full(
                val amount: BigDecimal,
                val rewardDestination: RewardDestination,
                val currentAccountAddress: String,
                validators: List<Validator>,
                collators: List<Collator>,
                selectionMethod: SelectionMethod
            ) : Payload(validators, collators, selectionMethod) {

                override fun changeValidators(
                    newValidators: List<Validator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return Full(amount, rewardDestination, currentAccountAddress, newValidators, collators, selectionMethod)
                }

                override fun changeCollators(
                    newCollators: List<Collator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return Full(amount, rewardDestination, currentAccountAddress, validators, newCollators, selectionMethod)
                }
            }

            class ExistingStash(
                validators: List<Validator>,
                collators: List<Collator>,
                selectionMethod: SelectionMethod
            ) : Payload(validators, collators, selectionMethod) {

                override fun changeValidators(
                    newValidators: List<Validator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return ExistingStash(newValidators, collators, selectionMethod)
                }

                override fun changeCollators(
                    newCollators: List<Collator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return ExistingStash(validators, newCollators, selectionMethod)
                }
            }

            class Validators(
                validators: List<Validator>,
                collators: List<Collator>,
                selectionMethod: SelectionMethod
            ) : Payload(validators, collators, selectionMethod) {

                override fun changeValidators(
                    newValidators: List<Validator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return Validators(newValidators, collators, selectionMethod)
                }

                override fun changeCollators(
                    newCollators: List<Collator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return this
                }
            }

            class Collators(
                validators: List<Validator>,
                collators: List<Collator>,
                selectionMethod: SelectionMethod
            ) : Payload(validators, collators, selectionMethod) {

                override fun changeValidators(
                    newValidators: List<Validator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return Collators(newValidators, collators, selectionMethod)
                }

                override fun changeCollators(
                    newCollators: List<Collator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return Collators(validators, newCollators, selectionMethod)
                }
            }

            abstract fun changeValidators(newValidators: List<Validator>, selectionMethod: SelectionMethod): Payload
            abstract fun changeCollators(newCollators: List<Collator>, selectionMethod: SelectionMethod): Payload
        }

        fun changeValidators(
            newValidators: List<Validator>,
            selectionMethod: SelectionMethod
        ) = ReadyToSubmit(payload.changeValidators(newValidators, selectionMethod))

        fun changeCollators(
            newCollators: List<Collator>,
            selectionMethod: SelectionMethod
        ) = ReadyToSubmit(payload.changeCollators(newCollators, selectionMethod))

        fun previous(): Validators {
            val payload = with(payload) {
                when (this) {
                    is Payload.Full -> Validators.Payload.Full(amount, rewardDestination, currentAccountAddress)
                    is Payload.ExistingStash -> Validators.Payload.ExistingStash
                    is Payload.Validators -> Validators.Payload.Validators

                    is Payload.Collators -> Validators.Payload.Validators
                }
            }

            return Validators(payload)
        }

        fun finish() = Initial
    }
}

class SetupStakingSharedState {

    val setupStakingProcess = MutableStateFlow<SetupStakingProcess>(SetupStakingProcess.Initial)

    fun set(newState: SetupStakingProcess) {
        Log.d("RX", "${setupStakingProcess.value.javaClass.simpleName} -> ${newState.javaClass.simpleName}")

        setupStakingProcess.value = newState
    }

    inline fun <reified T : SetupStakingProcess> get(): T = setupStakingProcess.value as T

    fun mutate(mutation: (SetupStakingProcess) -> SetupStakingProcess) {
        set(mutation(get()))
    }
}
