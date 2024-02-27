package jp.co.soramitsu.nft.data.pagination

interface PageBackStack {

    sealed interface Request {
        val page: String?

        fun updatePage(page: String?): Request

        object ReplayCurrent : Request {
            override val page: String = "Proceed"
            override fun updatePage(page: String?): Request = ReplayCurrent
        }

        @JvmInline
        value class Prev(override val page: String?) : Request {
            override fun updatePage(page: String?): Request = Prev(page)
        }

        class Next(override val page: String?, val size: Int) : Request {
            override fun updatePage(page: String?): Request = Next(page, size)
        }

        companion object {
            @Suppress("FunctionName")
            fun FromStart(defaultPageSize: Int) = Next(null, defaultPageSize)
        }
    }

    sealed interface PageResult<T> {

        class NoPrevPages<T> : PageResult<T>

        class NoNextPages<T> : PageResult<T>

        interface ValidPage<T> : PageResult<T> {
            val nextPage: String?

            val items: Sequence<T>

            fun updateItems(items: Sequence<T>): PageResult<T>
        }
    }

    suspend fun <ResponseItem> runPagedRequest(
        request: Request,
        block: suspend (page: String?, size: Int) -> PageResult.ValidPage<ResponseItem>
    ): PageResult<ResponseItem>

    suspend fun clean()
}
