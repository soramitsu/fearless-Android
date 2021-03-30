package jp.co.soramitsu.feature_staking_impl.presentation.common

import android.util.Log
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.model.StashSetup
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

sealed class SetupStakingProcess {

    class Initial : SetupStakingProcess() {

        val defaultAmount = 10.toBigDecimal()

        fun next(amount: BigDecimal) = Stash(amount)

        fun next(amount: BigDecimal, setup: StashSetup) = Validators(amount, setup)
    }

    class Stash(val amount: BigDecimal) : SetupStakingProcess() {

        fun previous() = Initial()

        fun next(stashSetup: StashSetup) = Validators(amount, stashSetup)
    }

    class Validators(
        val amount: BigDecimal,
        val stashSetup: StashSetup,
    ) : SetupStakingProcess() {

        fun previous() = if (stashSetup.alreadyHasStash) {
            Initial()
        } else {
            Stash(amount)
        }

        fun next(validators: List<Validator>) = Confirm(amount, stashSetup, validators)
    }

    class Confirm(
        val amount: BigDecimal,
        val stashSetup: StashSetup,
        val validators: List<Validator>,
    ) : SetupStakingProcess() {

        fun previous() = Validators(amount, stashSetup)

        fun finish() = Initial()
    }
}

class SetupStakingSharedState {

    val setupStakingProcess = MutableStateFlow<SetupStakingProcess>(SetupStakingProcess.Initial())

    fun set(newState: SetupStakingProcess) {
        Log.d("RX", "${setupStakingProcess.value.javaClass.simpleName} -> ${newState.javaClass.simpleName}")

        setupStakingProcess.value = newState
    }

    inline fun <reified T : SetupStakingProcess> get(): T = setupStakingProcess.value as T
}
