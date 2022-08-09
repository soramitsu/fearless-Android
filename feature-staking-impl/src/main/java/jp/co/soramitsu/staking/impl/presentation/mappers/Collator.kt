package jp.co.soramitsu.staking.impl.presentation.mappers

import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.staking.api.domain.model.CandidateInfo
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount

suspend fun mapCollatorToCollatorModel(
    collator: Collator,
    iconGenerator: AddressIconGenerator,
    token: Token,
    sorting: BlockProducersSorting<Collator>,
    selectedCollatorAddress: String?
) = mapCollatorToCollatorModel(
    collator,
    selectedCollatorAddress,
    { iconGenerator.createEthereumAddressModel(it, AddressIconGenerator.SIZE_MEDIUM, collator.identity?.display) },
    token,
    sorting
)

suspend fun mapCollatorToCollatorModel(
    collator: Collator,
    selectedCollatorAddress: String?,
    createIcon: suspend (address: String) -> AddressModel,
    token: Token,
    sorting: BlockProducersSorting<Collator>
): CollatorModel {
    val addressModel = createIcon(collator.address)

    return with(collator) {
        val scoring = sorting.toScoring(this, token)

        CollatorModel(
            accountIdHex = address,
            slashed = false,
            image = addressModel.image,
            address = addressModel.address,
            scoring = scoring,
            title = addressModel.nameOrAddress,
            isChecked = selectedCollatorAddress?.let { it == address },
            collator = collator
        )
    }
}

fun CandidateInfo.toCollator(address: String, identity: Identity?, apy: BigDecimal?) = Collator(
    address = address,
    bond = bond,
    delegationCount = delegationCount,
    totalCounted = totalCounted,
    lowestTopDelegationAmount = lowestTopDelegationAmount,
    highestBottomDelegationAmount = highestBottomDelegationAmount,
    lowestBottomDelegationAmount = lowestBottomDelegationAmount,
    topCapacity = topCapacity,
    bottomCapacity = bottomCapacity,
    request = request,
    status = status,
    identity = identity,
    apy = apy
)

fun BlockProducersSorting<Collator>.toScoring(collator: Collator, token: Token): CollatorModel.Scoring {
    return when (this) {
        BlockProducersSorting.CollatorSorting.APYSorting -> {
            val apy = collator.apy ?: BigDecimal.ZERO
            CollatorModel.Scoring.OneField(apy.formatAsPercentage())
        }
        BlockProducersSorting.CollatorSorting.CollatorsOwnStakeSorting -> {
            val ownStakeFormatted = token.amountFromPlanks(collator.bond)
            CollatorModel.Scoring.TwoFields(
                ownStakeFormatted.formatTokenAmount(token.configuration),
                token.fiatAmount(ownStakeFormatted)?.formatAsCurrency(token.fiatSymbol)
            )
        }
        BlockProducersSorting.CollatorSorting.DelegationsSorting -> {
            CollatorModel.Scoring.OneField(collator.delegationCount.toString())
        }
        BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting -> {
            val totalCountedFormatted = token.amountFromPlanks(collator.totalCounted)
            CollatorModel.Scoring.TwoFields(
                totalCountedFormatted.formatTokenAmount(token.configuration),
                token.fiatAmount(totalCountedFormatted)?.formatAsCurrency(token.fiatSymbol)
            )
        }
        BlockProducersSorting.CollatorSorting.MinimumBondSorting -> {
            val totalCountedFormatted = token.amountFromPlanks(collator.lowestTopDelegationAmount)
            CollatorModel.Scoring.TwoFields(
                totalCountedFormatted.formatTokenAmount(token.configuration),
                token.fiatAmount(totalCountedFormatted)?.formatAsCurrency(token.fiatSymbol)
            )
        }
        else -> error("Wrong sorting type")
    }
}
