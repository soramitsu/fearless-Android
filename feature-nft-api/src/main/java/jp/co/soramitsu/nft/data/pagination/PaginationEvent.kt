package jp.co.soramitsu.nft.data.pagination

sealed interface PaginationEvent<T> {

    class AllPreviousPagesLoaded<T> : PaginationEvent<T>

    @JvmInline
    value class PageIsLoaded<T>(
        val data: T
    ) : PaginationEvent<T>

    class AllNextPagesLoaded<T> : PaginationEvent<T>
}
