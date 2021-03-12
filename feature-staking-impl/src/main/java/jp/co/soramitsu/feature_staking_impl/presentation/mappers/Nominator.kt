package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.NominatorParcelModel

fun mapNominatorToNominatorParcelModel(nominator: IndividualExposure): NominatorParcelModel {
    return with(nominator) {
        NominatorParcelModel(who, value)
    }
}
