package jp.co.soramitsu.staking.impl.presentation.common.filters

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings.SettingsSchema
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ValidatorsSettingsViewModel @Inject constructor(
    private val settingsStorage: SettingsStorage,
    private val router: StakingRouter,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val resourceManager: ResourceManager
) : BaseViewModel(), ValidatorsSettingsScreenInterface {

    private val recommendationSettingsProvider: Deferred<RecommendationSettingsProvider<Validator>> by lazyAsync {
        recommendationSettingsProviderFactory.createRelayChain(router.currentStackEntryLifecycle)
    }

    val viewState = settingsStorage.schema.map { schema ->
        val filters = schema.filters.map { it.toViewState() }
        val sortings = schema.sortings.map { it.toViewState() }
        ValidatorsSettingsViewState(filters, sortings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ValidatorsSettingsViewState(listOf(), listOf()))

    private fun SettingsSchema.Filter.toViewState() = FilterItemViewState(resourceManager.getString(title), checked, filter)
    private fun SettingsSchema.Sorting.toViewState() = SortingItemViewState(resourceManager.getString(title), checked, sorting)

    override fun onClose() {
        router.back()
    }

    override fun onFilterSelected(item: FilterItemViewState) {
        viewModelScope.launch {
            settingsStorage.filterSelected(item.filter)
            val settingsProvider = recommendationSettingsProvider()
            settingsProvider.settingsChanged(settingsStorage.schema.first(), BigInteger.ZERO)
        }
    }

    override fun onSortingSelected(item: SortingItemViewState) {
        viewModelScope.launch {
            settingsStorage.sortingSelected(item.sorting)
            val settingsProvider = recommendationSettingsProvider()
            settingsProvider.settingsChanged(settingsStorage.schema.first(), BigInteger.ZERO)
        }
    }
}
