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
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsWithStakeFlagParcelModel
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
        gropedList.mapKeys { (statusGroup, _) -> mapNominatedValidatorStatusToUiModel(statusGroup) }
            .mapValues { (_, nominatedValidators) -> nominatedValidators.map { mapNominatedValidatorToUiModel(it, token) } }
            .toListWithHeaders()
    }
        .withLoading()
        .inBackground()
        .asLiveData()

    val shouldShowOversubscribedNoRewardWarning = groupedCurrentValidatorsFlow.map { groupedList ->
        val (_, validators) = groupedList.entries.first { (group, _) -> group is NominatedValidator.Status.Group.Active }

        validators.any { (it.status as NominatedValidator.Status.Active).willUserBeRewarded.not() }
    }
        .inBackground()
        .share()

    private suspend fun mapNominatedValidatorToUiModel(nominatedValidator: NominatedValidator, token: Token): NominatedValidatorModel {
        val validator = nominatedValidator.validator

        val nominationFormatted = (nominatedValidator.status as? NominatedValidator.Status.Active)?.let { activeStatus ->
            val amountFormatted = token.configuration.amountFromPlanks(activeStatus.nomination).formatTokenAmount(token.configuration)

            resourceManager.getString(R.string.staking_your_nominated_format, amountFormatted)
        }

        val validatorAddress = validator.accountIdHex.fromHex().toAddress(token.configuration.networkType)

        return NominatedValidatorModel(
            addressModel = iconGenerator.createAddressModel(validatorAddress, AddressIconGenerator.SIZE_MEDIUM, validator.identity?.display),
            nominated = nominationFormatted,
            isOversubscribed = validator.electedInfo?.isOversubscribed ?: false,
            isSlashed = validator.slashed
        )
    }

    private fun mapNominatedValidatorStatusToUiModel(statusGroup: NominatedValidator.Status.Group) = when (statusGroup) {

        is NominatedValidator.Status.Group.Active -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(
                    R.string.staking_your_elected_format, statusGroup.numberOfValidators
                ),
                R.color.green
            ),
            resourceManager.getString(R.string.staking_your_allocated_description)
        )

        is NominatedValidator.Status.Group.Inactive -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(R.string.staking_your_not_elected_format, statusGroup.numberOfValidators),
                R.color.black1
            ),
            resourceManager.getString(R.string.staking_your_inactive_description)
        )

        is NominatedValidator.Status.Group.Elected -> NominatedValidatorStatusModel(
            null,
            resourceManager.getString(R.string.staking_your_not_allocated_description)
        )

        is NominatedValidator.Status.Group.WaitingForNextEra -> NominatedValidatorStatusModel(
            TitleConfig(
                resourceManager.getString(
                    R.string.staking_custom_header_validators_title,
                    statusGroup.numberOfValidators,
                    statusGroup.maxValidatorsPerNominator
                ),
                R.color.black1
            ),
            resourceManager.getString(R.string.staking_your_validators_changing_title)
        )
    }

    fun changeClicked() {
        launch {
            val currentState = setupStakingSharedState.get<SetupStakingProcess.Initial>()

            val currentValidators = flattenCurrentValidators.first().map(NominatedValidator::validator)

            val newState = currentState.changeValidatorsFlow()
                .next(currentValidators, SelectionMethod.CUSTOM)

            setupStakingSharedState.set(newState)

            router.openStartChangeValidators()
        }
    }

    fun backClicked() {
        router.back()
    }

    fun validatorInfoClicked(address: String) = launch {
        val payload = withContext(Dispatchers.Default) {
            val accountId = address.toHexAccountId()
            val allValidators = flattenCurrentValidators.first()

            val nominatedValidator = allValidators.first { it.validator.accountIdHex == accountId }

            mapValidatorToValidatorDetailsWithStakeFlagParcelModel(nominatedValidator)
        }

        router.openValidatorDetails(payload)
    }
}
