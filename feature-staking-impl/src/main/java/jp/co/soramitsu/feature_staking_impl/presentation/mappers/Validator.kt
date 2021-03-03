package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.wallet.formatWithDefaultPrecision
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model.ValidatorModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks

private val PERCENT_MULTIPLIER = 100.toBigDecimal()

private const val ICON_SIZE_DP = 24

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
        ValidatorDetailsParcelModel(accountIdHex, totalStake, ownStake, identityModel, nominators, apy)
    }
}

fun mapValidatorDetailsParcelToValidatorDetailsModel(validator: ValidatorDetailsParcelModel, asset: Asset): ValidatorDetailsModel {
    return with(validator) {
        val token = asset.token

        val totalStake = token.amountFromPlanks(validator.totalStake)

        val totalStakeFormatted = totalStake.formatWithDefaultPrecision(asset.token.type)
        val totalStakeFiatFormatted = token.fiatAmount(totalStake)?.formatAsCurrency()

        val address = validator.accountIdHex.fromHex().toAddress(token.type.networkType)

        val identity = identity?.let(::mapIdentityParcelModelToIdentityModel)

        val nominatorsCountFormatted = nominators.size.toString()

        val apyPercentageFormatted = (PERCENT_MULTIPLIER * apy).formatAsPercentage()

        ValidatorDetailsModel(
            totalStakeFormatted,
            totalStakeFiatFormatted,
            address,
            identity,
            nominatorsCountFormatted,
            apyPercentageFormatted
        )
    }
}
