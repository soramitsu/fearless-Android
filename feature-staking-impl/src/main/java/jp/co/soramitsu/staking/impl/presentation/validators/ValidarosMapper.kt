package jp.co.soramitsu.staking.impl.presentation.validators

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.presentation.validators.compose.ListSegmentState
import jp.co.soramitsu.staking.impl.presentation.validators.compose.ValidatorsListViewState
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.Asset

fun buildSegmentedValidatorsListState(
    resourceManager: ResourceManager,
    validators: List<Validator>,
    settings: RecommendationSettings<Validator>?,
    searchQuery: String,
    selectMode: SelectValidatorFlowState.ValidatorSelectMode,
    selectedValidators: Collection<String>,
    asset: Asset
): ValidatorsListViewState {
    val sorting = when (settings?.sorting) {
        BlockProducersSorting.ValidatorSorting.APYSorting -> resourceManager.getString(R.string.staking_rewards_apy)
        BlockProducersSorting.ValidatorSorting.TotalStakeSorting -> resourceManager.getString(R.string.staking_validator_total_stake)
        BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting -> resourceManager.getString(
            R.string.staking_filter_title_own_stake
        )

        else -> resourceManager.getString(R.string.staking_rewards_apy)
    }

    val filteredBySearchQueryValidators = validators.asSequence().filter {
        val searchQueryLowerCase = searchQuery.lowercase()
        val identityNameLowerCase = it.identity?.display?.lowercase().orEmpty()
        val addressLowerCase = it.address.lowercase()

        identityNameLowerCase.contains(searchQueryLowerCase) || addressLowerCase.contains(
            searchQueryLowerCase
        )
    }

    val electedValidatorModels = filteredBySearchQueryValidators
        .filter { it.electedInfo != null }
        .toList()
        .map { electedValidator ->
            val isSelected = when (selectMode) {
                SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> electedValidator.accountIdHex in selectedValidators
                SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> true
            }
            electedValidator.toModel(
                isSelected,
                settings?.sorting,
                asset,
                resourceManager
            )
        }

    val electedValidatorsSegment = electedValidatorModels.takeIf { it.isNotEmpty() }
        ?.let { notEmptyElectedValidatorModels ->
            ListSegmentState(
                title = resourceManager.getString(
                    R.string.staking_your_elected_format,
                    electedValidatorModels.size
                ),
                iconRes = R.drawable.ic_elected_validator,
                sortingValue = sorting,
                items = notEmptyElectedValidatorModels
            )
        }

    val notElectedValidatorModels = filteredBySearchQueryValidators
        .filter { it.electedInfo == null }
        .toList()
        .map { electedValidator ->
            val isSelected = when (selectMode) {
                SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> electedValidator.accountIdHex in selectedValidators
                SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> true
            }
            electedValidator.toNotElectedModel(
                isSelected,
                resourceManager
            )
        }

    val notElectedValidatorsSegment = notElectedValidatorModels.takeIf { it.isNotEmpty() }
        ?.let { notEmptyNotElectedValidatorModels ->
            ListSegmentState(
                title = resourceManager.getString(
                    R.string.staking_your_not_elected_format,
                    notElectedValidatorModels.size
                ),
                iconRes = R.drawable.ic_validator_waiting,
                sortingValue = null,
                items = notEmptyNotElectedValidatorModels
            )
        }

    val segments = listOfNotNull(electedValidatorsSegment, notElectedValidatorsSegment)
    val selected = segments.map { it.items }.flatten().filter { it.isSelected }

    return ValidatorsListViewState(
        segments = segments,
        selectedItems = selected
    )
}

fun Validator.toModel(
    isSelected: Boolean,
    sortingCaption: BlockProducersSorting<Validator>?,
    asset: Asset,
    resourceManager: ResourceManager
): SelectableListItemState<String> {
    val totalStake =
        electedInfo?.totalStake.orZero().formatCryptoFromPlanks(asset.token.configuration)
    val ownStake = electedInfo?.ownStake.orZero().formatCryptoFromPlanks(asset.token.configuration)

    val captionHeader = when (sortingCaption) {
        BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting -> resourceManager.getString(
            R.string.staking_filter_title_own_stake
        )

        else -> resourceManager.getString(R.string.staking_rewards_apy)
    }
    val captionValue = when (sortingCaption) {
        BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting -> ownStake
        else -> electedInfo?.apy.orZero().fractionToPercentage().formatAsPercentage()
    }
    val captionText = buildAnnotatedString {
        withStyle(style = SpanStyle(color = black1)) {
            append("$captionHeader ")
        }
        withStyle(style = SpanStyle(color = greenText)) {
            append(captionValue)
        }
    }

    val additionalStatuses = if (this.slashed || this.prefs?.blocked == true || this.electedInfo?.isOversubscribed == true) {
        listOf(SelectableListItemState.SelectableListItemAdditionalStatus.WARNING)
    } else {
        emptyList()
    }

    return SelectableListItemState(
        id = accountIdHex,
        title = identity?.display ?: address,
        subtitle = resourceManager.getString(
            R.string.staking_validator_total_stake_token,
            totalStake
        ),
        caption = captionText,
        isSelected = isSelected,
        additionalStatuses = additionalStatuses
    )
}

fun Validator.toNotElectedModel(
    isSelected: Boolean,
    resourceManager: ResourceManager
): SelectableListItemState<String> {

    val captionText = buildAnnotatedString {
        withStyle(style = SpanStyle(color = black1)) {
            append("${resourceManager.getString(R.string.common_commission)} ")
        }
        withStyle(style = SpanStyle(color = greenText)) {
            append(
                this@toNotElectedModel.prefs?.commission?.fractionToPercentage()
                    ?.formatAsPercentage()
            )
        }
    }

    val additionalStatuses = if (this.prefs?.blocked == true) {
        listOf(SelectableListItemState.SelectableListItemAdditionalStatus.WARNING)
    } else {
        emptyList()
    }

    return SelectableListItemState(
        id = accountIdHex,
        title = identity?.display ?: address,
        caption = captionText,
        isSelected = isSelected,
        additionalStatuses = additionalStatuses
    )
}
