package jp.co.soramitsu.nft.data.pagination

sealed interface PaginationRequest {

    @JvmInline
    value class Start(val pageLimit: Int) : PaginationRequest

    object ProceedFromLastPage : PaginationRequest

    object Prev : PaginationRequest

    @JvmInline
    value class Next(val pageLimit: Int) : PaginationRequest
}
