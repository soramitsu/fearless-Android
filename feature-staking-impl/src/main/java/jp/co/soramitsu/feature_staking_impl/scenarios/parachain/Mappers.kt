package jp.co.soramitsu.feature_staking_impl.scenarios.parachain

import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationCollatorSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.EffectiveAmountBondedSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.TotalStakeSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.ValidatorOwnStakeSorting
import jp.co.soramitsu.feature_staking_impl.scenarios.BlockProducer
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.amountInPlanksToScoring
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun List<Collator>.toBlockProducer(
    chain: Chain,
    selectedCollators: Set<Collator>,
    token: Token,
    recommendationSettings: RecommendationSorting = APYSorting
): List<BlockProducer> {
    return map { collator ->
        mapCollatorToBlockProducer(
            chain = chain,
            collator = collator,
            token = token,
            isChecked = collator in selectedCollators,
            sorting = recommendationSettings
        )
    }
}

fun mapCollatorToBlockProducer(
    chain: Chain,
    collator: Collator,
    token: Token,
    sorting: BlockProducersSorting<Collator> = BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting,
    isChecked: Boolean,
): BlockProducer {

    val address = collator.address

    return with(collator) {
        val scoring = when (sorting) {
            BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting -> {
                val primary = token.amountFromPlanks(totalCounted)
                val primaryFormatted = primary.formatTokenAmount(token.configuration)
                val secondary = token.fiatAmount(primary)?.formatAsCurrency(token.fiatSymbol)
                BlockProducer.Scoring.TwoFields(primaryFormatted, secondary)
            }
            BlockProducersSorting.APYSorting<Collator> -> {
                apy?.let {
                    val apyPercentage = it.fractionToPercentage().formatAsPercentage()

                    BlockProducer.Scoring.OneField(apyPercentage)
                }
            }

            else -> throw NotImplementedError("Unsupported sorting: $sorting")
        }

        BlockProducer(
            accountIdHex = accountIdHex,
            slashed = slashed,
            address = address,
            scoring = scoring,
            title = validator.identity?.display ?: address,
            isChecked = isChecked
        )
    }
}
