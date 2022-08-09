package jp.co.soramitsu.featurestakingimpl.presentation.mappers

import jp.co.soramitsu.featurestakingapi.domain.model.IndividualExposure
import jp.co.soramitsu.featurestakingimpl.presentation.validators.parcel.NominatorParcelModel

fun mapNominatorToNominatorParcelModel(nominator: IndividualExposure): NominatorParcelModel {
    return with(nominator) {
        NominatorParcelModel(who, value)
    }
}
