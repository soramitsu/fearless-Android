package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model

class IdentityModel(
    val display: String?,
    val legal: String?,
    val web: String?,
    val riot: String?,
    val email: String?,
    val pgpFingerprint: String?,
    val image: String?,
    val twitter: String?
) {
    val isEmptyExceptName = legal == null &&
        web == null &&
        riot == null &&
        email == null &&
        pgpFingerprint == null &&
        image == null &&
        twitter == null

    val isEmpty = display == null && isEmptyExceptName
}
