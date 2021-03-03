package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.wallet.formatWithDefaultPrecision
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model.ValidatorModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks

private val PERCENT_MULTIPLIER = 100.toBigDecimal()

fun mapValidatorToValidatorModel(validator: Validator, addressModel: AddressModel): ValidatorModel {
    return with(validator) {
        val apyPercentage = (PERCENT_MULTIPLIER * apy).formatAsPercentage()
        ValidatorModel(
            accountIdHex = accountIdHex,
            slashed = slashed,
            identity = identity,
            image = addressModel.image,
            address = addressModel.address,
            apy = apyPercentage,
            title = identity?.display ?: addressModel.address
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
        val amount = asset.token.amountFromPlanks(validator.ownStake)
        val totalStake = amount.formatWithDefaultPrecision(asset.token.type)
        val totalStakeFiat = amount.multiply(asset.token.dollarRate).formatAsCurrency()
        val address = validator.accountIdHex.fromHex().toAddress(asset.token.type.networkType)
        val identity = identity?.let(::mapIdentityParcelModelToIdentityModel)
        val nominatorsCount = nominators.size.toString()
        val apyPercentage = (PERCENT_MULTIPLIER * apy).formatAsPercentage()
        ValidatorDetailsModel(totalStake, totalStakeFiat, address, identity, nominatorsCount, apyPercentage)
    }
}
