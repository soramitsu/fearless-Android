package jp.co.soramitsu.common.data.model

data class CursorPage<T>(
    val curPageNumber: Long,
    val endReached: Boolean,
    val items: List<T>
) : List<T> by items
