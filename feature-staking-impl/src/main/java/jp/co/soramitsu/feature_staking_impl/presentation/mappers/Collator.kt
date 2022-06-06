package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.feature_staking_api.domain.model.CandidateInfo
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger

private const val ICON_SIZE_DP = 24

suspend fun mapCollatorToCollatorModel(
    chain: Chain,
    collator: Collator,
    iconGenerator: AddressIconGenerator,
    token: Token,
    isChecked: Boolean? = null,
    sorting: RecommendationSorting = APYSorting,
    selectedCollatorAddress: String?,
) = mapCollatorToCollatorModel(
    chain,
    collator,
    selectedCollatorAddress,
    { iconGenerator.createAddressModel(it, ICON_SIZE_DP, collator.identity?.display) },
    token,
    sorting,
)

suspend fun mapCollatorToCollatorModel(
    chain: Chain,
    collator: Collator,
    selectedCollatorAddress: String?,
    createIcon: suspend (address: String) -> AddressModel,
    token: Token,
    sorting: RecommendationSorting = APYSorting,
): CollatorModel {
    val addressModel = createIcon(collator.address)

    return with(collator) {
        val scoring = CollatorModel.Scoring.OneField("stub")

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

fun CandidateInfo.toCollator(address: String, identity: Identity?, minDelegation: BigInteger) = Collator(
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
    minFromTopDelegations = minDelegation,
)
