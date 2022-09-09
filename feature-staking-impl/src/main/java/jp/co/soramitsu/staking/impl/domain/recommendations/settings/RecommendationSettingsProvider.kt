package jp.co.soramitsu.staking.impl.domain.recommendations.settings

import java.math.BigInteger
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.BlockProducerFilters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.RecommendationPostProcessor
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.postprocessors.RemoveClusteringPostprocessor
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings.SettingsSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

abstract class RecommendationSettingsProvider<T> {

    protected abstract val alwaysEnabledFilters: List<BlockProducerFilters<T>>

    protected abstract val customizableFilters: List<BlockProducerFilters<T>>

    protected abstract val allPostProcessors: List<RecommendationPostProcessor<T>>

    protected abstract val sorting: List<BlockProducersSorting<T>>

    private val customSettingsFlow by lazy { MutableStateFlow(defaultSelectCustomSettings()) }

    fun setCustomSettings(filters: List<BlockProducerFilters<T>>, sorting: BlockProducersSorting<T>) {
        val current = customSettingsFlow.value

        val new = current.copy(
            customEnabledFilters = filters,
            sorting = sorting
        )
        customSettingsFlow.value = new
    }

    fun observeRecommendationSettings(): Flow<RecommendationSettings<T>> = customSettingsFlow

    fun currentSettings(): RecommendationSettings<T> = customSettingsFlow.value

    abstract fun defaultSettings(): RecommendationSettings<T>

    abstract fun defaultSelectCustomSettings(): RecommendationSettings<T>

    abstract fun settingsChanged(schema: SettingsSchema, amount: BigInteger)

    class RelayChain(
        private val maximumRewardedNominators: Int,
        private val maximumValidatorsPerNominator: Int
    ) : RecommendationSettingsProvider<Validator>() {

        override val alwaysEnabledFilters = listOf(
            BlockProducerFilters.ValidatorFilter.HasBlocked
        )

        override val customizableFilters: List<BlockProducerFilters<Validator>> = listOf(
            BlockProducerFilters.ValidatorFilter.NotSlashedFilter,
            BlockProducerFilters.ValidatorFilter.HasIdentity,
            BlockProducerFilters.ValidatorFilter.NotOverSubscribedFilter(maximumRewardedNominators)
        )

        override val allPostProcessors = listOf(
            RemoveClusteringPostprocessor
        )

        override val sorting: List<BlockProducersSorting<Validator>> = listOf(
            BlockProducersSorting.ValidatorSorting.APYSorting,
            BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting,
            BlockProducersSorting.ValidatorSorting.TotalStakeSorting
        )

        override fun defaultSettings(): RecommendationSettings<Validator> {
            return RecommendationSettings(
                alwaysEnabledFilters = alwaysEnabledFilters,
                customEnabledFilters = emptyList(),
                sorting = BlockProducersSorting.ValidatorSorting.APYSorting,
                postProcessors = allPostProcessors,
                limit = maximumValidatorsPerNominator
            )
        }

        override fun defaultSelectCustomSettings() = RecommendationSettings(
            alwaysEnabledFilters = alwaysEnabledFilters,
            customEnabledFilters = emptyList(),
            sorting = BlockProducersSorting.ValidatorSorting.APYSorting,
            postProcessors = allPostProcessors,
            limit = null
        )

        override fun settingsChanged(schema: SettingsSchema, amount: BigInteger) {
            val filters = schema.filters.filter { it.checked }.mapNotNull {
                when (it.filter) {
                    Filters.HavingOnChainIdentity -> BlockProducerFilters.ValidatorFilter.HasIdentity
                    Filters.NotSlashedFilter -> BlockProducerFilters.ValidatorFilter.NotSlashedFilter
                    Filters.NotOverSubscribed -> BlockProducerFilters.ValidatorFilter.NotOverSubscribedFilter(maximumRewardedNominators)
                    else -> null
                }
            }
            val sorting = schema.sortings.firstOrNull { it.checked }.let {
                when (it?.sorting) {
                    Sorting.EstimatedRewards -> BlockProducersSorting.ValidatorSorting.APYSorting
                    Sorting.TotalStake -> BlockProducersSorting.ValidatorSorting.TotalStakeSorting
                    Sorting.ValidatorsOwnStake -> BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting
                    else -> null
                } as? BlockProducersSorting<Validator>
            }
            sorting?.let { setCustomSettings(filters, it) }
        }
    }

    class Parachain(
        maxTopDelegationPerCandidate: Int,
        private val maxDelegationsPerDelegator: Int
    ) : RecommendationSettingsProvider<Collator>() {

        override val alwaysEnabledFilters = emptyList<BlockProducerFilters.CollatorFilter>()

        override val customizableFilters = listOf(
            BlockProducerFilters.CollatorFilter.HavingOnChainIdentity,
            BlockProducerFilters.CollatorFilter.NotOverSubscribed
        )

        override val allPostProcessors = emptyList<RecommendationPostProcessor<Collator>>()

        override val sorting: List<BlockProducersSorting<Collator>> = listOf(
            BlockProducersSorting.CollatorSorting.APYSorting,
            BlockProducersSorting.CollatorSorting.CollatorsOwnStakeSorting,
            BlockProducersSorting.CollatorSorting.MinimumBondSorting,
            BlockProducersSorting.CollatorSorting.DelegationsSorting,
            BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting
        )

        override fun defaultSettings(): RecommendationSettings<Collator> {
            return RecommendationSettings(
                alwaysEnabledFilters = alwaysEnabledFilters,
                customEnabledFilters = emptyList(),
                sorting = BlockProducersSorting.CollatorSorting.APYSorting,
                postProcessors = allPostProcessors,
                limit = maxDelegationsPerDelegator
            )
        }

        override fun defaultSelectCustomSettings() = RecommendationSettings(
            alwaysEnabledFilters = alwaysEnabledFilters,
            customEnabledFilters = emptyList(),
            sorting = BlockProducersSorting.CollatorSorting.APYSorting,
            postProcessors = allPostProcessors,
            limit = null
        )

        override fun settingsChanged(schema: SettingsSchema, amount: BigInteger) {
            val filters = schema.filters.filter { it.checked }.mapNotNull {
                when (it.filter) {
                    Filters.HavingOnChainIdentity -> BlockProducerFilters.CollatorFilter.HavingOnChainIdentity
                    Filters.NotOverSubscribed -> BlockProducerFilters.CollatorFilter.NotOverSubscribed
                    Filters.WithRelevantBond -> BlockProducerFilters.CollatorFilter.WithRelevantBond(amount)
                    else -> null
                }
            }
            val sorting = schema.sortings.first { it.checked }.let {
                when (it.sorting) {
                    Sorting.EstimatedRewards -> BlockProducersSorting.CollatorSorting.APYSorting
                    Sorting.EffectiveAmountBonded -> BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting
                    Sorting.CollatorsOwnStake -> BlockProducersSorting.CollatorSorting.CollatorsOwnStakeSorting
                    Sorting.Delegations -> BlockProducersSorting.CollatorSorting.DelegationsSorting
                    Sorting.MinimumBond -> BlockProducersSorting.CollatorSorting.MinimumBondSorting
                    else -> null
                } as BlockProducersSorting<Collator>
            }
            setCustomSettings(filters, sorting)
        }
    }
}
