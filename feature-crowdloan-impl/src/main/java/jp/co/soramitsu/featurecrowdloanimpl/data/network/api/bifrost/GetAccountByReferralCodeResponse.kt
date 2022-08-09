package jp.co.soramitsu.featurecrowdloanimpl.data.network.api.bifrost

class GetAccountByReferralCodeResponse(
    val getAccountByInvitationCode: GetAccountByReferralCode
) {

    class GetAccountByReferralCode(val account: String?)
}
