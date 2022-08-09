package jp.co.soramitsu.featurewalletapi.domain.interfaces

enum class TransactionFilter {
    EXTRINSIC, REWARD, TRANSFER
}

fun Set<TransactionFilter>.allFiltersIncluded(): Boolean {
    return size == TransactionFilter.values().size
}
