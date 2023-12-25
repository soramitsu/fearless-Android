package jp.co.soramitsu.nft.data.models.requests

sealed interface PaginationRequest {

    object NextPage: PaginationRequest

    data class NextPageSized(
        val pageSize: Int
    ): PaginationRequest

}