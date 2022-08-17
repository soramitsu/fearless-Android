package jp.co.soramitsu.featurestakingimpl.presentation.validators.current.model

import androidx.annotation.ColorRes
import jp.co.soramitsu.common.address.AddressModel

data class NominatedValidatorStatusModel(
    val titleConfig: TitleConfig?,
    val description: String
) {
    data class TitleConfig(
        val text: String,
        @ColorRes val colorRes: Int
    )
}

class NominatedValidatorModel(
    val addressModel: AddressModel,
    val nominated: String?,
    val isOversubscribed: Boolean,
    val isSlashed: Boolean
)
