package jp.co.soramitsu.feature_wallet_api.domain.model

class RecipientSearchResult(
    val myAccounts: List<Account>,
    val contacts: List<String>
) {

    class Account(
        val name: String?,
        val address: String
    )
}