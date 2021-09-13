package jp.co.soramitsu.feature_wallet_api.domain.interfaces

enum class TransactionFilter {
    EXTRINSIC, REWARD, TRANSFER
}

fun Set<TransactionFilter>.allFiltersIncluded(): Boolean {
    return size == TransactionFilter.values().size
}
