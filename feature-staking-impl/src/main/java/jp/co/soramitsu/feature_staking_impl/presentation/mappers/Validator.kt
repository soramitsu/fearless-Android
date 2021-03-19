package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorStakeModel
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
        val apyPercentage = (PERCENT_MULTIPLIER * apy).formatAsPercentage()

        ValidatorModel(
            accountIdHex = accountIdHex,
            slashed = slashed,
            identity = identity,
            image = addressModel.image,
            address = addressModel.address,
            apy = apyPercentage,
            title = addressModel.nameOrAddress
        )
    }
}

fun mapValidatorToValidatorDetailsParcelModel(validator: Validator): ValidatorDetailsParcelModel {
    return with(validator) {
        val nominators = nominatorStakes.map(::mapNominatorToNominatorParcelModel)
        val identityModel = identity?.let(::mapIdentityToIdentityParcelModel)
        val stakeModel = ValidatorStakeParcelModel(totalStake, ownStake, nominators, apy)
        ValidatorDetailsParcelModel(accountIdHex, stakeModel, identityModel)
    }
}

suspend fun mapValidatorDetailsParcelToValidatorDetailsModel(
    validator: ValidatorDetailsParcelModel,
    asset: Asset,
    iconGenerator: AddressIconGenerator
): ValidatorDetailsModel {
    return with(validator) {
        val token = asset.token

        val address = validator.accountIdHex.fromHex().toAddress(token.type.networkType)

        val addressImage = iconGenerator.createAddressModel(address, ICON_DETAILS_SIZE_DP)

        val identity = identity?.let(::mapIdentityParcelModelToIdentityModel)

        val stake = validator.stake?.let {
            val totalStake = token.amountFromPlanks(it.totalStake)
            val totalStakeFormatted = totalStake.formatWithDefaultPrecision(asset.token.type)
            val totalStakeFiatFormatted = token.fiatAmount(totalStake)?.formatAsCurrency()
            val nominatorsCountFormatted = it.nominators.size.toString()
            val apyPercentageFormatted = (PERCENT_MULTIPLIER * it.apy).formatAsPercentage()

            ValidatorStakeModel(totalStakeFormatted, totalStakeFiatFormatted, nominatorsCountFormatted, apyPercentageFormatted)
        }

        ValidatorDetailsModel(
            stake,
            address,
            addressImage.image,
            identity
        )
    }
}
