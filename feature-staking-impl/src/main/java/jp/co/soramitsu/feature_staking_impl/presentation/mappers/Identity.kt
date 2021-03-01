package jp.co.soramitsu.feature_staking_impl.presentation.mappers

import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.IdentityModel

fun mapIdentityToIdentityModel(identity: Identity): IdentityModel {
    return with(identity) {
        IdentityModel(display, legal, web, riot, email, pgpFingerprint, image, twitter)
    }
}