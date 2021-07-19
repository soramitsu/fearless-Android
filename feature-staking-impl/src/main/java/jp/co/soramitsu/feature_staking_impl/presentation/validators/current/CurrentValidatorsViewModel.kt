package jp.co.soramitsu.feature_staking_impl.presentation.validators.current

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.list.toListWithHeaders
import jp.co.soramitsu.common.list.toValueList
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.NominatedValidator
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorStatusModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorStatusModel.TitleConfig
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CurrentValidatorsViewModel(
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val stakingInteractor: StakingInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val currentValidatorsInteractor: CurrentValidatorsInteractor,
    private val setupStakingSharedState: SetupStakingSharedState,
) : BaseViewModel() {

    private val groupedCurrentValidatorsFlow = stakingInteractor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash>()
        .flatMapLatest(currentValidatorsInteractor::nominatedValidatorsFlow)
        .inBackground()
        .share()

    private val flattenCurrentValidators = groupedCurrentValidatorsFlow
        .map { it.toValueList() }
        .inBackground()
        .share()

    val tokenFlow = stakingInteractor.currentAssetFlow()
        .map { it.token }
        .share()

    val currentValidatorModelsLiveData = groupedCurrentValidatorsFlow.combine(tokenFlow) { gropedList, token ->
        gropedList.mapKeys { (status, validators) -> mapNominatedValidatorStatusToUiModel(status, validators.size) }
            .mapValues { (_, nominatedValidators) -> nominatedValidators.map { mapNominatedValidatorToUiModel(it, token) } }
            .toListWithHeaders()
    }
        .withLoading()
        .inBackground()
        .asLiveData()

    private suspend fun mapNominatedValidatorToUiModel(nominatedValidator: NominatedValidator, token: Token): NominatedValidatorModel {
        val validator = nominatedValidator.validator

        val nominationFormatted = nominatedValidator.nominationInPlanks?.let {
            val amountFormatted = token.type.amountFromPlanks(it).formatTokenAmount(token.type)

            resourceManager.getString(R.string.staking_nominated, amountFormatted)
        }

        val validatorAddress = validator.accountIdHex.fromHex().toAddress(token.type.networkType)

        return NominatedValidatorModel(
            addressModel = iconGenerator.createAddressModel(validatorAddress, AddressIconGenerator.SIZE_MEDIUM, validator.identity?.display),
            nominated = nominationFormatted
        )
    }

    private fun mapNominatedValidatorStatusToUiModel(status: NominatedValidator.Status, valuesSize: Int) = when (status) {
        NominatedValidator.Status.Active -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(R.string.common_active_with_count, valuesSize),
                R.color.green
            ),
            resourceManager.getString(R.string.staking_active_validators_description)
        )

        NominatedValidator.Status.Inactive -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(R.string.staking_inactive_validators_format, valuesSize),
                R.color.black1
            ),
            resourceManager.getString(R.string.staking_inactive_validators_description)
        )

        NominatedValidator.Status.Elected -> NominatedValidatorStatusModel(
            null,
            resourceManager.getString(R.string.staking_elected_validators_description)
        )

        is NominatedValidator.Status.WaitingForNextEra -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(R.string.staking_selected_validators_format, valuesSize, status.maxValidatorsPerNominator),
                R.color.black1
            ),
            resourceManager.getString(R.string.staking_waiting_validators_description)
        )
    }

    fun changeClicked() {
        val currentState = setupStakingSharedState.get<SetupStakingProcess.Initial>()
        setupStakingSharedState.set(currentState.changeValidatorsFlow())

        router.openStartChangeValidators()
    }

    fun backClicked() {
        router.back()
    }

    fun validatorInfoClicked(address: String) = launch {
        val payload = withContext(Dispatchers.Default) {
            val accountId = address.toHexAccountId()
            val allValidators = flattenCurrentValidators.first()

            val nominatedValidator = allValidators.first { it.validator.accountIdHex == accountId }

            mapValidatorToValidatorDetailsParcelModel(nominatedValidator.validator)
        }

        router.openValidatorDetails(payload)
    }
}
