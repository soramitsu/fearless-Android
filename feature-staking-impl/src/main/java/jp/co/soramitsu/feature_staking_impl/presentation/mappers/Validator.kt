package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorDetailsModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorModel

private val PERCENT_MULTIPLIER = 100.toBigDecimal()

fun mapValidatorToValidatorModel(validator: Validator, addressModel: AddressModel): ValidatorModel {
    return with(validator) {
        val apyPercentage = (PERCENT_MULTIPLIER * apy).formatAsPercentage()
        ValidatorModel(
            slashed = slashed,
            identity = identity,
            image = addressModel.image,
            address = addressModel.address,
            apy = apyPercentage,
            title = identity?.display ?: addressModel.address
        )
    }
}

fun mapValidatorToValidatorDetailsModel(validator: Validator, addressModel: AddressModel): ValidatorDetailsModel {
    return with(validator) {
        val apyPercentage = (PERCENT_MULTIPLIER * apy).formatAsPercentage()
        val nominators = nominatorStakes.map(::mapNominatorToNominatorModel)
        val identityModel = identity?.let(::mapIdentityToIdentityModel)
        ValidatorDetailsModel(identityModel, nominators, apyPercentage)
    }
}