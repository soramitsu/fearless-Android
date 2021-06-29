package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.toggle
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.TotalStakeSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.ValidatorOwnStakeSorting
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select.model.ContinueButtonState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.setValidators
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.retractValidators
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SelectCustomValidatorsViewModel(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val interactor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val tokenUseCase: TokenUseCase,
) : BaseViewModel() {

    private val validatorRecommendator by lazy {
        async { validatorRecommendatorFactory.create(router.currentStackEntryLifecycle) }
    }

    private val recommendationSettingsFlow = flow {
        emitAll(recommendationSettingsProvider().observeRecommendationSettings())
    }.share()

    private val shownValidators = recommendationSettingsFlow.map {
        recommendator().recommendations(it)
    }.share()

    private val tokenFlow = tokenUseCase.currentTokenFlow()
        .inBackground()
        .share()

    private val selectedValidators = MutableStateFlow(emptySet<Validator>())

    private val maxSelectedValidators = interactor.maxValidatorsPerNominator()

    private val iconsCache: MutableMap<String, AddressModel> = mutableMapOf()

    val validatorModelsFlow = combine(
        shownValidators,
        selectedValidators,
        tokenFlow,
        ::convertToModels
    ).inBackground().share()

    val selectedTitle = shownValidators.map {
        resourceManager.getString(R.string.staking_shown_validators_format, it.size, recommendator().availableValidators.size)
    }.inBackground().share()

    val buttonState = selectedValidators.map {
        if (it.isEmpty()) {
            ContinueButtonState(
                enabled = false,
                text = resourceManager.getString(R.string.staking_select_validators_with_max, maxSelectedValidators)
            )
        } else {
            ContinueButtonState(
                enabled = true,
                text = resourceManager.getString(R.string.staking_show_selected, it.size, maxSelectedValidators)
            )
        }
    }

    val scoringHeader = recommendationSettingsFlow.map {
        when (it.sorting) {
            APYSorting -> resourceManager.getString(R.string.staking_rewards_apy)
            TotalStakeSorting -> resourceManager.getString(R.string.staking_sorting_header_total_stake)
            ValidatorOwnStakeSorting -> resourceManager.getString(R.string.staking_sorting_header_own_stake)
            else -> throw IllegalArgumentException("Unknown sorting: ${it.sorting}")
        }
    }.inBackground().share()

    val fillWithRecommendedEnabled = selectedValidators.map { it.size < maxSelectedValidators }
        .share()

    val clearFiltersEnabled = recommendationSettingsFlow.map { it.filters.isNotEmpty() || it.postProcessors.isNotEmpty() }
        .share()

    val deselectAllEnabled = selectedValidators.map { it.isNotEmpty() }
        .share()

    init {
        observeExternalSelectionChanges()
    }

    fun backClicked() {
        setupStakingSharedState.retractValidators()

        router.back()
    }

    fun nextClicked() {
        updateSetupStakingState()

        router.openReviewCustomValidators()
    }

    fun validatorInfoClicked(validatorModel: ValidatorModel) {
        router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(validatorModel.validator))
    }

    fun validatorClicked(validatorModel: ValidatorModel) {
        mutateSelected {
            it.toggle(validatorModel.validator)
        }
    }

    fun settingsClicked() {
        router.openCustomValidatorsSettings()
    }

    fun searchClicked() {
        updateSetupStakingState()

        router.openSearchCustomValidators()
    }

    private fun updateSetupStakingState() {
        setupStakingSharedState.setValidators(selectedValidators.value.toList())
    }

    fun clearFilters() {
        launch {
            val settings = recommendationSettingsProvider().createModifiedCustomValidatorsSettings(
                filterIncluder = { false },
                postProcessorIncluder = { false }
            )

            recommendationSettingsProvider().setCustomValidatorsSettings(settings)
        }
    }

    fun deselectAll() {
        mutateSelected { emptySet() }
    }

    fun fillRestWithRecommended() {
        mutateSelected { selected ->
            val recommended = recommendator().recommendations(recommendationSettingsProvider().defaultSettings())

            val missingFromRecommended = recommended.toSet() - selected
            val neededToFill = maxSelectedValidators - selected.size

            selected + missingFromRecommended.take(neededToFill).toSet()
        }
    }

    private fun observeExternalSelectionChanges() {
        setupStakingSharedState.setupStakingProcess
            .filterIsInstance<SetupStakingProcess.Confirm>()
            .onEach { selectedValidators.value = it.payload.validators.toSet() }
            .launchIn(viewModelScope)
    }

    private suspend fun convertToModels(
        validators: List<Validator>,
        selectedValidators: Set<Validator>,
        token: Token
    ): List<ValidatorModel> {
        return validators.map { validator ->
            mapValidatorToValidatorModel(
                validator = validator,
                createIcon = {
                    iconsCache.getOrPut(it) {
                        addressIconGenerator.createAddressModel(it, AddressIconGenerator.SIZE_MEDIUM, validator.identity?.display)
                    }
                },
                token = token,
                isChecked = validator in selectedValidators,
                sorting = recommendationSettingsFlow.first().sorting
            )
        }
    }

    private suspend fun recommendator() = validatorRecommendator.await()

    private fun mutateSelected(mutation: suspend (Set<Validator>) -> Set<Validator>) {
        launch {
            selectedValidators.value = mutation(selectedValidators.value)
        }
    }

    private suspend fun recommendationSettingsProvider() = recommendationSettingsProviderFactory.get()
}
