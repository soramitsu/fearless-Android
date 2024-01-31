package jp.co.soramitsu.nft.data.pagination

sealed interface Page {

    object NoPrevPages : Page

    @JvmInline
    value class ValidPage(
        val key: String?
    ) : Page

    object NoNextPages : Page
}

suspend inline fun <T> Page.mapToPaginationEvent(crossinline transform: suspend (String?) -> T): PaginationEvent<T> {
    return when (this) {
        is Page.NoPrevPages ->
            PaginationEvent.AllPreviousPagesLoaded()

        is Page.NoNextPages ->
            PaginationEvent.AllNextPagesLoaded()

        is Page.ValidPage ->
            PaginationEvent.PageIsLoaded(transform.invoke(key))
    }
}
