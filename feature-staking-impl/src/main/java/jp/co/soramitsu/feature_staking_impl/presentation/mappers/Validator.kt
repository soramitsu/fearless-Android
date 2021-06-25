package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.ValidatorOwnStakeSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.TotalStakeSorting
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorStakeModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorStakeModel.ActiveStakeModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorStakeParcelModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import java.math.BigInteger

private val PERCENT_MULTIPLIER = 100.toBigDecimal()

private const val ICON_SIZE_DP = 24
private const val ICON_DETAILS_SIZE_DP = 32

suspend fun mapValidatorToValidatorModel(
    validator: Validator,
    iconGenerator: AddressIconGenerator,
    token: Token,
    isChecked: Boolean? = null,
    sorting: RecommendationSorting = APYSorting,
) = mapValidatorToValidatorModel(
    validator,
    { iconGenerator.createAddressModel(it, ICON_SIZE_DP, validator.identity?.display) },
    token,
    isChecked,
    sorting
)

suspend fun mapValidatorToValidatorModel(
    validator: Validator,
    createIcon: suspend (address: String) -> AddressModel,
    token: Token,
    isChecked: Boolean? = null,
    sorting: RecommendationSorting = APYSorting,
): ValidatorModel {
    val networkType = token.type.networkType
    val address = validator.accountIdHex.fromHex().toAddress(networkType)
    val addressModel = createIcon(address)

    return with(validator) {
        val scoring = when (sorting) {
            APYSorting -> {
                electedInfo?.apy?.let {
                    val apyPercentage = it.fractionToPercentage().formatAsPercentage()

                    ValidatorModel.Scoring.OneField(apyPercentage)
                }
            }

            TotalStakeSorting -> stakeToScoring(electedInfo?.totalStake, token)

            ValidatorOwnStakeSorting -> stakeToScoring(electedInfo?.ownStake, token)

            else -> throw NotImplementedError("Unsupported sorting: $sorting")
        }

        ValidatorModel(
            accountIdHex = accountIdHex,
            slashed = slashed,
            image = addressModel.image,
            address = addressModel.address,
            scoring = scoring,
            title = addressModel.nameOrAddress,
            isChecked = isChecked,
            validator = validator
        )
    }
}

private fun stakeToScoring(stakeInPlanks: BigInteger?, token: Token): ValidatorModel.Scoring.TwoFields? {
    if (stakeInPlanks == null) return null

    val stake = token.amountFromPlanks(stakeInPlanks)

    return ValidatorModel.Scoring.TwoFields(
        primary = stake.formatTokenAmount(token.type),
        secondary = token.fiatAmount(stake)?.formatAsCurrency()
    )
}

fun mapValidatorToValidatorDetailsParcelModel(
    validator: Validator
): ValidatorDetailsParcelModel {
    return with(validator) {
        val identityModel = identity?.let(::mapIdentityToIdentityParcelModel)

        val stakeModel = electedInfo?.let {
            val nominators = it.nominatorStakes.map(::mapNominatorToNominatorParcelModel)

            ValidatorStakeParcelModel.Active(it.totalStake, it.ownStake, nominators, it.apy)
        } ?: ValidatorStakeParcelModel.Inactive

        ValidatorDetailsParcelModel(accountIdHex, stakeModel, identityModel)
    }
}

suspend fun mapValidatorDetailsParcelToValidatorDetailsModel(
    validator: ValidatorDetailsParcelModel,
    asset: Asset,
    iconGenerator: AddressIconGenerator,
    resourceManager: ResourceManager
): ValidatorDetailsModel {
    return with(validator) {
        val token = asset.token

        val address = validator.accountIdHex.fromHex().toAddress(token.type.networkType)

        val addressImage = iconGenerator.createAddressModel(address, ICON_DETAILS_SIZE_DP)

        val identity = identity?.let(::mapIdentityParcelModelToIdentityModel)

        val stake = when (val stake = validator.stake) {

            ValidatorStakeParcelModel.Inactive -> ValidatorStakeModel(
                statusText = resourceManager.getString(R.string.staking_nominator_status_inactive),
                statusColorRes = R.color.gray2,
                activeStakeModel = null
            )

            is ValidatorStakeParcelModel.Active -> {
                val totalStake = token.amountFromPlanks(stake.totalStake)
                val totalStakeFormatted = totalStake.formatTokenAmount(asset.token.type)
                val totalStakeFiatFormatted = token.fiatAmount(totalStake)?.formatAsCurrency()
                val nominatorsCountFormatted = stake.nominators.size.toString()
                val apyPercentageFormatted = (PERCENT_MULTIPLIER * stake.apy).formatAsPercentage()

                ValidatorStakeModel(
                    statusText = resourceManager.getString(R.string.staking_nominator_status_active),
                    statusColorRes = R.color.green,
                    activeStakeModel = ActiveStakeModel(
                        totalStake = totalStakeFormatted,
                        totalStakeFiat = totalStakeFiatFormatted,
                        nominatorsCount = nominatorsCountFormatted,
                        apy = apyPercentageFormatted
                    )
                )
            }
        }

        ValidatorDetailsModel(
            stake,
            address,
            addressImage.image,
            identity
        )
    }
}
