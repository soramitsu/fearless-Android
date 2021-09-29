package jp.co.soramitsu.feature_account_impl.domain.account.details

class ChainProjection(
    val address: String,
    val from: From
) {

    enum class From {
        META_ACCOUNT, CHAIN_ACCOUNT
    }
}
