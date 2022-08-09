package jp.co.soramitsu.featurestakingimpl.presentation.validators.change.custom.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.featurestakingimpl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.featurestakingimpl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.featurestakingimpl.presentation.StakingRouter
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.featurewalletapi.domain.TokenUseCase
import jp.co.soramitsu.featurewalletapi.domain.model.planksFromAmount
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class CustomValidatorsSettingsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    @Named("StakingTokenUseCase") private val tokenUseCase: TokenUseCase,
    private val settingsStorage: SettingsStorage,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val stakingType = savedStateHandle.get<Chain.Asset.StakingType>(CustomValidatorsSettingsFragment.STAKING_TYPE_KEY)!!

    private val recommendationSettingsProvider by lazyAsync {
        recommendationSettingsProviderFactory.create(router.currentStackEntryLifecycle, stakingType)
    }

    private val initialSettingsFlow = flow {
        emit(settingsStorage.schema.first())
    }

    val settingsSchemaLiveData: LiveData<SettingsSchema> = settingsStorage.schema.map { schema ->
        schema.copy(filters = schema.filters.filter { it.filter !in settingsStorage.quickFilters })
    }.asLiveData()

//    val tokenNameFlow = tokenUseCase.currentTokenFlow().map { it.configuration.name }

    private val isSettingsModifiedFlow: Flow<Boolean> = combine(
        settingsStorage.schema,
        initialSettingsFlow
    ) { currentSchema, initialSchema ->
        currentSchema != initialSchema
    }

    val isApplyButtonEnabled = isSettingsModifiedFlow

    val isResetButtonEnabled: Flow<Boolean> = isSettingsModifiedFlow

    fun reset() {
        settingsStorage.resetSorting()
        settingsStorage.resetFilters()
    }

    fun applyChanges() {
        viewModelScope.launch {
            val state = setupStakingSharedState.getOrNull<SetupStakingProcess.SelectBlockProducersStep.Validators>()
            val payload = (state?.payload ?: setupStakingSharedState.getOrNull<SetupStakingProcess.SelectBlockProducersStep.Collators>()?.payload)
                as? SetupStakingProcess.SelectBlockProducersStep.Payload.Full
            val amount = payload?.amount
            val config = tokenUseCase.currentToken().configuration
            val amountInPlanks = amount?.let { config.planksFromAmount(amount) } ?: BigInteger.ZERO
            recommendationSettingsProvider().settingsChanged(settingsStorage.schema.first(), amountInPlanks)
            router.back()
        }
    }

    fun backClicked() {
        router.back()
    }

    fun onFilterChecked(checkedFilter: SettingsSchema.Filter) {
        settingsStorage.filterSelected(checkedFilter.filter)
    }

    fun onSortingChecked(checkedSorting: SettingsSchema.Sorting) {
        settingsStorage.sortingSelected(checkedSorting.sorting)
    }
}
