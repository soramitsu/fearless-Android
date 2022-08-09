package jp.co.soramitsu.featurestakingimpl.presentation.validators.change

import jp.co.soramitsu.featurestakingapi.domain.model.Collator
import jp.co.soramitsu.featurestakingapi.domain.model.Validator
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingSharedState

fun SetupStakingSharedState.retractValidators() = mutate {
    when (it) {
        is SetupStakingProcess.ReadyToSubmit<*> -> it.previous()
        is SetupStakingProcess.SelectBlockProducersStep.Validators -> it.previous()
        is SetupStakingProcess.Initial -> it
        else -> throw IllegalArgumentException("Cannot retract validators from $it state")
    }
}

fun SetupStakingSharedState.setCustomValidators(
    validators: List<Validator>
) = setValidators(validators, SelectionMethod.CUSTOM)

fun SetupStakingSharedState.setRecommendedValidators(
    validators: List<Validator>
) = setValidators(validators, SelectionMethod.RECOMMENDED)

private fun SetupStakingSharedState.setValidators(
    validators: List<Validator>,
    selectionMethod: SelectionMethod
) = mutate {
    when (it) {
        is SetupStakingProcess.SelectBlockProducersStep.Validators -> it.next(validators, selectionMethod)
        is SetupStakingProcess.ReadyToSubmit.Stash -> it.changeBlockProducers(validators, selectionMethod)
        else -> throw IllegalArgumentException("Cannot set validators from $it state")
    }
}

fun SetupStakingSharedState.setRecommendedCollators(
    collators: List<Collator>
) = setCollators(collators, SelectionMethod.RECOMMENDED)

fun SetupStakingSharedState.setCustomCollators(
    collators: List<Collator>
) = setCollators(collators, SelectionMethod.CUSTOM)

private fun SetupStakingSharedState.setCollators(
    collators: List<Collator>,
    selectionMethod: SelectionMethod
) = mutate {
    when (it) {
        is SetupStakingProcess.SelectBlockProducersStep.Collators -> it.next(collators, selectionMethod)
        is SetupStakingProcess.ReadyToSubmit.Parachain -> it.changeBlockProducers(collators, selectionMethod)
        else -> throw IllegalArgumentException("Cannot set collators from $it state")
    }
}
