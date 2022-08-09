package jp.co.soramitsu.featurecrowdloanimpl.data.network.api.bifrost

class BifrostReferralCheckRequest(code: String) {
    val query = """
        {
            getAccountByInvitationCode(code: "$code") {
                account
            }
        }
    """.trimIndent()
}
