package jp.co.soramitsu.feature_staking_impl.scenarios.relaychain

import java.math.BigInteger
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.TotalStakeSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.ValidatorOwnStakeSorting
import jp.co.soramitsu.feature_staking_impl.scenarios.BlockProducer
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain


fun List<Validator>.toBlockProducer(
    chain: Chain,
    selectedValidators: Set<Validator>,
    token: Token,
    recommendationSettings: RecommendationSorting = APYSorting
): List<BlockProducer> {
    return map { validator ->
        mapValidatorToBlockProducer(
            chain = chain,
            validator = validator,
            token = token,
            isChecked = validator in selectedValidators,
            sorting = recommendationSettings
        )
    }
}

fun mapValidatorToBlockProducer(
    chain: Chain,
    validator: Validator,
    token: Token,
    sorting: RecommendationSorting = APYSorting,
    isChecked: Boolean,
): BlockProducer {
    val address = chain.addressOf(validator.accountIdHex.fromHex())

    return with(validator) {
        val scoring = when (sorting) {
            APYSorting -> {
                electedInfo?.apy?.let {
                    val apyPercentage = it.fractionToPercentage().formatAsPercentage()

                    BlockProducer.Scoring.OneField(apyPercentage)
                }
            }

            TotalStakeSorting -> amountInPlanksToScoring(electedInfo?.totalStake, token)

            ValidatorOwnStakeSorting -> amountInPlanksToScoring(electedInfo?.ownStake, token)

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

fun amountInPlanksToScoring(amountInPlanks: BigInteger?, token: Token): BlockProducer.Scoring.TwoFields? {
    if (amountInPlanks == null) return null

    val stake = token.amountFromPlanks(amountInPlanks)

    return BlockProducer.Scoring.TwoFields(
        primary = stake.formatTokenAmount(token.configuration),
        secondary = token.fiatAmount(stake)?.formatAsCurrency(token.fiatSymbol)
    )
}
