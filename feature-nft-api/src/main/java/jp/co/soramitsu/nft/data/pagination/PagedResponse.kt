package jp.co.soramitsu.nft.data.pagination

class PagedResponse<T>(
    val tag: Any,
    val request: PageBackStack.Request,
    val result: Result<PageBackStack.PageResult<T>>
) {
    fun updateResult(result: Result<PageBackStack.PageResult<T>>): PagedResponse<T> =
        PagedResponse(tag, request, result)
}
