package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters

import jp.co.soramitsu.feature_staking_api.domain.model.CandidateCapacity
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings.SettingsSchema

abstract class BlockProducerFilters<T> {
    abstract fun shouldInclude(model: T): Boolean

    sealed class ValidatorFilter : BlockProducerFilters<Validator>() {
        object HasIdentity : ValidatorFilter() {
            override fun shouldInclude(model: Validator): Boolean {
                return model.identity != null
            }
        }

        object HasBlocked : ValidatorFilter() {
            override fun shouldInclude(model: Validator) = model.prefs?.blocked?.not() ?: false
        }

        class NotOverSubscribedFilter(
            private val maxSubscribers: Int
        ) : ValidatorFilter() {

            override fun shouldInclude(model: Validator): Boolean {
                val electedInfo = model.electedInfo

                return if (electedInfo != null) {
                    electedInfo.nominatorStakes.size < maxSubscribers
                } else {
                    throw IllegalStateException("Filtering validator ${model.accountIdHex} with no prefs")
                }
            }
        }

        object NotSlashedFilter : ValidatorFilter() {

            override fun shouldInclude(model: Validator): Boolean {
                return !model.slashed
            }
        }
    }

    sealed class CollatorFilter : BlockProducerFilters<Collator>() {
        object HavingOnChainIdentity : CollatorFilter() {
            override fun shouldInclude(model: Collator): Boolean {
                return model.identity != null
            }
        }

        object NotOverSubscribed : CollatorFilter() {
            override fun shouldInclude(model: Collator): Boolean {
                return model.topCapacity != CandidateCapacity.Full
            }
        }
    }
}

fun <T> List<T>.applyFilters(filters: List<BlockProducerFilters<T>>): List<T> {
    return filter { item -> filters.all { filter -> filter.shouldInclude(item) } }
}

interface RecommendationPostProcessor<T> {
    fun invoke(original: List<T>): List<T>
}

enum class Filters {
    HavingOnChainIdentity, NotOverSubscribed, NotSlashedFilter, // LimitOf2ValidatorsPerIdentity
}

enum class Sorting {
    EstimatedRewards, TotalStake, ValidatorsOwnStake, EffectiveAmountBonded, CollatorsOwnStake, Delegations, MinimumBond
}

fun Collection<Filters>.toFiltersSchema(selectedFilters: Set<Filters>) = map { it.toModel(selectedFilters) }

fun Filters.toModel(selectedFilters: Set<Filters>): SettingsSchema.Filter {
    val title = when (this) {
        Filters.HavingOnChainIdentity -> R.string.staking_recommended_feature_3
        Filters.NotOverSubscribed -> R.string.staking_recommended_feature_2
        Filters.NotSlashedFilter -> R.string.staking_recommended_feature_4
    }

    return SettingsSchema.Filter(title, this in selectedFilters, this)
}

fun Collection<Sorting>.toSortingSchema(selectedSorting: Sorting) = map { it.toModel(selectedSorting) }

fun Sorting.toModel(selectedSorting: Sorting): SettingsSchema.Sorting {
    val title = when (this) {
        Sorting.EstimatedRewards -> R.string.staking_custom_validators_settings_sort_apy
        Sorting.TotalStake -> R.string.staking_validator_total_stake_token
        Sorting.ValidatorsOwnStake -> R.string.staking_filter_title_own_stake_token
        Sorting.EffectiveAmountBonded -> R.string.collator_staking_sorting_effective_amount_bonded
        Sorting.CollatorsOwnStake -> R.string.collator_staking_sorting_own_stake
        Sorting.Delegations -> R.string.collator_staking_sorting_delegations
        Sorting.MinimumBond -> R.string.collator_staking_sorting_minimum_bond
    }

    return SettingsSchema.Sorting(title, this == selectedSorting, this)
}
