package jp.co.soramitsu.common.data.holders

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot

interface RuntimeHolder {

    suspend fun runtime(): RuntimeSnapshot
}

suspend inline fun <T> RuntimeHolder.useRuntime(block: (RuntimeSnapshot) -> T) = block(runtime())
