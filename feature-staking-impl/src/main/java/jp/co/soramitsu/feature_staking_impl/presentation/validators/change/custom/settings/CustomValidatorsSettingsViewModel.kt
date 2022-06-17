package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class CustomValidatorsSettingsViewModel(
    private val router: StakingRouter,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    tokenUseCase: TokenUseCase,
    private val stakingType: Chain.Asset.StakingType,
    private val settingsStorage: SettingsStorage
) : BaseViewModel() {

    private val recommendationSettingsProvider by lazyAsync {
        recommendationSettingsProviderFactory.create(router.currentStackEntryLifecycle, stakingType)
    }

    private val initialSettingsFlow = flow {
        emit(settingsStorage.schema.first())
    }

    val settingsSchemaLiveData: LiveData<SettingsSchema> = settingsStorage.schema.asLiveData()

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
            recommendationSettingsProvider().settingsChanged(settingsStorage.schema.first())
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
