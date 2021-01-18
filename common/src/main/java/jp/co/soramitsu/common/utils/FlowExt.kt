package jp.co.soramitsu.common.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun <T, R> Flow<List<T>>.mapList(mapper: (T) -> R) = map { it.map(mapper) }