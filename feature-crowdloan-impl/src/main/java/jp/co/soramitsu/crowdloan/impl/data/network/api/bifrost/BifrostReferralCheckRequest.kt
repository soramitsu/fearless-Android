package jp.co.soramitsu.crowdloan.impl.data.network.api.bifrost

class BifrostReferralCheckRequest(code: String) {
    val query = """
        {
            getAccountByInvitationCode(code: "$code") {
                account
            }
        }
    """.trimIndent()
}
