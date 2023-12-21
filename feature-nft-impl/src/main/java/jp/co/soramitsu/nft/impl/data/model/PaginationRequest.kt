package jp.co.soramitsu.nft.impl.data.model

sealed interface PaginationRequest {

    object NextPage: PaginationRequest

    data class NextPageSized(
        val pageSize: Int
    ): PaginationRequest

}