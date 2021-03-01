package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.NominatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorDetailsModel

fun mapNominatorToNominatorModel(nominator: IndividualExposure): NominatorModel {
    return with(nominator) {
        NominatorModel(who, value)
    }
}