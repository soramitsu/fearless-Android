package jp.co.soramitsu.common.utils

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private const val CORE_POOL_SIZE = 1
private const val KEEP_ALIVE_TIME = 60L

fun newLimitedThreadPoolExecutor(maxThreads: Int): ThreadPoolExecutor {
    return ThreadPoolExecutor(CORE_POOL_SIZE, maxThreads, KEEP_ALIVE_TIME, TimeUnit.SECONDS, LinkedBlockingQueue())
}
