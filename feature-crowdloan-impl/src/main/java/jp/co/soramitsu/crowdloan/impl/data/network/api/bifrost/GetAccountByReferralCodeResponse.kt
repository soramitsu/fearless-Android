package jp.co.soramitsu.crowdloan.impl.data.network.api.bifrost

class GetAccountByReferralCodeResponse(
    val getAccountByInvitationCode: GetAccountByReferralCode
) {

    class GetAccountByReferralCode(val account: String?)
}
