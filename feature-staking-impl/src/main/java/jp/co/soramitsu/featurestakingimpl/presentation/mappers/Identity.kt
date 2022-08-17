package jp.co.soramitsu.featurestakingimpl.presentation.mappers

import jp.co.soramitsu.featurestakingapi.domain.model.Identity
import jp.co.soramitsu.featurestakingimpl.presentation.validators.details.model.IdentityModel
import jp.co.soramitsu.featurestakingimpl.presentation.validators.parcel.IdentityParcelModel

fun mapIdentityToIdentityParcelModel(identity: Identity): IdentityParcelModel {
    return with(identity) {
        IdentityParcelModel(display, legal, web, riot, email, pgpFingerprint, image, twitter)
    }
}

fun mapIdentityParcelModelToIdentityModel(identity: IdentityParcelModel): IdentityModel {
    return with(identity) {
        IdentityModel(display, legal, web, riot, email, pgpFingerprint, image, twitter)
    }
}
