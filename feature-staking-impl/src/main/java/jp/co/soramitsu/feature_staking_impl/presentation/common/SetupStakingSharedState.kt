package jp.co.soramitsu.feature_staking_impl.presentation.common

import android.util.Log
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

sealed class SetupStakingProcess {

    object Initial : SetupStakingProcess() {

        val defaultAmount = 10.toBigDecimal()

        fun fullFlow(amount: BigDecimal) = Stash(amount)

        fun existingStashFlow() = Validators(Validators.Payload.ExistingStash)

        fun changeValidatorsFlow() = Validators(Validators.Payload.Validators)
    }

    class Stash(val amount: BigDecimal) : SetupStakingProcess() {

        fun previous() = Initial

        fun next(
            newAmount: BigDecimal,
            rewardDestination: RewardDestination,
            currentAccountAddress: String
        ) = Validators(Validators.Payload.Full(newAmount, rewardDestination, currentAccountAddress))
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
            is Payload.Full -> Stash(payload.amount)
            else -> Initial
        }

        fun next(validators: List<Validator>, selectionMethod: ReadyToSubmit.SelectionMethod): SetupStakingProcess {
            val payload = with(payload) {
                when (this) {
                    is Payload.Full -> ReadyToSubmit.Payload.Full(amount, rewardDestination, controllerAddress, validators, selectionMethod)
                    is Payload.ExistingStash -> ReadyToSubmit.Payload.ExistingStash(validators, selectionMethod)
                    is Payload.Validators -> ReadyToSubmit.Payload.Validators(validators, selectionMethod)
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
            val selectionMethod: SelectionMethod
        ) {

            class Full(
                val amount: BigDecimal,
                val rewardDestination: RewardDestination,
                val currentAccountAddress: String,
                validators: List<Validator>,
                selectionMethod: SelectionMethod
            ) : Payload(validators, selectionMethod) {

                override fun changeValidators(
                    newValidators: List<Validator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return Full(amount, rewardDestination, currentAccountAddress, newValidators, selectionMethod)
                }
            }

            class ExistingStash(
                validators: List<Validator>,
                selectionMethod: SelectionMethod
            ) : Payload(validators, selectionMethod) {

                override fun changeValidators(
                    newValidators: List<Validator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return ExistingStash(newValidators, selectionMethod)
                }
            }

            class Validators(
                validators: List<Validator>,
                selectionMethod: SelectionMethod
            ) : Payload(validators, selectionMethod) {

                override fun changeValidators(
                    newValidators: List<Validator>,
                    selectionMethod: SelectionMethod
                ): Payload {
                    return Validators(newValidators, selectionMethod)
                }
            }

            abstract fun changeValidators(newValidators: List<Validator>, selectionMethod: SelectionMethod): Payload
        }

        fun changeValidators(
            newValidators: List<Validator>,
            selectionMethod: SelectionMethod
        ) = ReadyToSubmit(payload.changeValidators(newValidators, selectionMethod))

        fun previous(): Validators {
            val payload = with(payload) {
                when (this) {
                    is Payload.Full -> Validators.Payload.Full(amount, rewardDestination, currentAccountAddress)
                    is Payload.ExistingStash -> Validators.Payload.ExistingStash
                    is Payload.Validators -> Validators.Payload.Validators
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
