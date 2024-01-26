package jp.co.soramitsu.nft.data.pagination

sealed interface PaginationRequest {

    companion object {
        val Start = Next.Specific(page = null)
    }

    sealed interface Prev: PaginationRequest {

        object Page: Prev {
            override fun toString(): String {
                return "PaginationRequest.Prev.Page"
            }
        }

        @JvmInline
        value class WithSize(
            val pageLimit: Int
        ): Prev

        class TwoBeforeSpecific(
            val page: String?,
            val pageLimit: Int? = null
        ): Prev {
            override fun toString(): String {
                return "PaginationRequest.Prev.TwoBeforeSpecific(page: $page, pageLimit: $pageLimit)"
            }
        }

    }

    sealed interface Next: PaginationRequest {

        object Page: Next {
            override fun toString(): String {
                return "PaginationRequest.Next.Page"
            }
        }

        @JvmInline
        value class WithSize(
            val pageLimit: Int
        ): Next

        class Specific(
            val page: String?,
            val pageLimit: Int? = null
        ): Next {
            override fun toString(): String {
                return "PaginationRequest.Next.Specific(page: $page, pageLimit: $pageLimit)"
            }
        }

    }

}
