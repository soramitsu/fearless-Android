package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorStakeModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorStakeModel.ActiveStakeModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model.ValidatorModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatWithDefaultPrecision

private val PERCENT_MULTIPLIER = 100.toBigDecimal()

private const val ICON_SIZE_DP = 24
private const val ICON_DETAILS_SIZE_DP = 32

suspend fun mapValidatorToValidatorModel(
    validator: Validator,
    iconGenerator: AddressIconGenerator,
    networkType: Node.NetworkType
): ValidatorModel {
    val address = validator.accountIdHex.fromHex().toAddress(networkType)
    val addressModel = iconGenerator.createAddressModel(address, ICON_SIZE_DP, validator.identity?.display)

    return with(validator) {
        val apyPercentage = electedInfo?.apy?.let { (PERCENT_MULTIPLIER * it).formatAsPercentage() }

        ValidatorModel(
            accountIdHex = accountIdHex,
            slashed = slashed,
            image = addressModel.image,
            address = addressModel.address,
            apy = apyPercentage,
            title = addressModel.nameOrAddress
        )
    }
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
                val totalStakeFormatted = totalStake.formatWithDefaultPrecision(asset.token.type)
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
