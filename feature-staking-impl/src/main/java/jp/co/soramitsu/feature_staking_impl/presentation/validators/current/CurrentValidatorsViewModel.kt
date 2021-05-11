package jp.co.soramitsu.feature_staking_impl.presentation.validators.current

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.list.flatten
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.NominatedValidator
import jp.co.soramitsu.feature_staking_api.domain.model.NominatedValidatorStatus
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorStatusModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorStatusModel.TitleConfig
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatWithDefaultPrecision
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class CurrentValidatorsViewModel(
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val stakingInteractor: StakingInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val currentValidatorsInteractor: CurrentValidatorsInteractor,
) : BaseViewModel() {

    private val currentValidatorsFlow = stakingInteractor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash.Nominator>()
        .flatMapLatest(currentValidatorsInteractor::nominatedValidatorsFlow)
        .inBackground()
        .share()

    val tokenFlow = stakingInteractor.currentAssetFlow()
        .map { it.token }
        .share()

    val currentValidatorModelsLiveData = currentValidatorsFlow.combine(tokenFlow) { gropedList, token ->
        gropedList.mapKeys { (status, validators) -> mapNominatedValidatorStatusToUiModel(status, validators.size) }
            .mapValues { (_, nominatedValidators) -> nominatedValidators.map { mapNominatedValidatorToUiModel(it, token) } }
            .flatten()
    }
        .withLoading()
        .inBackground()
        .asLiveData()

    private suspend fun mapNominatedValidatorToUiModel(nominatedValidator: NominatedValidator, token: Token): NominatedValidatorModel {
        val validator = nominatedValidator.validator

        val nominationFormatted = nominatedValidator.nominationInPlanks?.let {
            val amountFormatted = token.type.amountFromPlanks(it).formatWithDefaultPrecision(token.type)

            resourceManager.getString(R.string.staking_nominated, amountFormatted)
        }

        val validatorAddress = validator.accountIdHex.fromHex().toAddress(token.type.networkType)

        return NominatedValidatorModel(
            addressModel = iconGenerator.createAddressModel(validatorAddress, AddressIconGenerator.SIZE_MEDIUM, validator.identity?.display),
            nominated = nominationFormatted
        )
    }

    private fun mapNominatedValidatorStatusToUiModel(status: NominatedValidatorStatus, valuesSize: Int) = when (status) {
        NominatedValidatorStatus.Active -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(R.string.staking_active_validators_format, valuesSize),
                R.color.green
            ),
            resourceManager.getString(R.string.staking_active_validators_description)
        )

        NominatedValidatorStatus.Inactive -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(R.string.staking_inactive_validators_format, valuesSize),
                R.color.black1
            ),
            resourceManager.getString(R.string.staking_inactive_validators_description)
        )

        NominatedValidatorStatus.Elected -> NominatedValidatorStatusModel(
            null,
            resourceManager.getString(R.string.staking_elected_validators_description)
        )

        is NominatedValidatorStatus.WaitingForNextEra -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(R.string.staking_waiting_validators_format, valuesSize, status.maxValidatorsPerNominator),
                R.color.black1
            ),
            resourceManager.getString(R.string.staking_waiting_validators_description)
        )
    }

    fun backClicked() {
        router.back()
    }
}
