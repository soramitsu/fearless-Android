package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorModel

fun mapValidatorToValidatorModel(validator: Validator, addressModel: AddressModel): ValidatorModel {
    return with(validator) {
        ValidatorModel(
            slashed = slashed,
            identity = identity,
            image = addressModel.image,
            address = addressModel.address,
            apyDecimal = apy
        )
    }
}