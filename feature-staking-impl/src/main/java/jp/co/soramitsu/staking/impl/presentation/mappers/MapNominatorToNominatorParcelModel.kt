package jp.co.soramitsu.staking.impl.presentation.mappers

import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.NominatorParcelModel

fun mapNominatorToNominatorParcelModel(nominator: IndividualExposure): NominatorParcelModel {
    return with(nominator) {
        NominatorParcelModel(who, value)
    }
}
