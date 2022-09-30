package jp.co.soramitsu.wallet.impl.domain.interfaces

enum class TransactionFilter {
    EXTRINSIC, REWARD, TRANSFER
}

fun Set<TransactionFilter>.allFiltersIncluded(): Boolean {
    return size == TransactionFilter.values().size
}
