package jp.co.soramitsu.feature_staking_impl.data.mappers

import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorModel

private const val ICON_SIZE_DP = 24

suspend fun mapValidatorToValidatorModel(
    validator: Validator,
    iconGenerator: AddressIconGenerator,
    networkType: Node.NetworkType
): ValidatorModel {
    val address = validator.accountIdHex.fromHex().toAddress(networkType)
    val addressModel = iconGenerator.createAddressModel(address, ICON_SIZE_DP)

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