package jp.co.soramitsu.common.utils

import jp.co.soramitsu.fearless_utils.wsrpc.recovery.LinearReconnectStrategy
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.ReconnectStrategy
import kotlinx.coroutines.delay

suspend inline fun <T> retryUntilDone(
    retryStrategy: ReconnectStrategy = LinearReconnectStrategy(step = 500L),
    block: () -> T,
): T {
    var attempt = 0

    while (true) {
        val blockResult = runCatching { block() }

        if (blockResult.isSuccess) {
            return blockResult.requireValue()
        } else {
            attempt++

            delay(retryStrategy.getTimeForReconnect(attempt))
        }
    }
}
