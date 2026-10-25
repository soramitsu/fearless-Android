package jp.co.soramitsu.fearless_utils.wsrpc.recovery

import kotlin.math.pow

interface ReconnectStrategy {
    /**
     * The handling of attempt number is implementation-dependent, but in general, first attempt should have value of 1
     */
    fun getTimeForReconnect(attempt: Int): Long
}

class ConstantReconnectStrategy(private val step: Long) : ReconnectStrategy {

    override fun getTimeForReconnect(attempt: Int) = step
}

class LinearReconnectStrategy(private val step: Long) : ReconnectStrategy {

    override fun getTimeForReconnect(attempt: Int) = attempt * step
}

class ExponentialReconnectStrategy(
    private val initialTime: Long,
    private val base: Double
) : ReconnectStrategy {

    override fun getTimeForReconnect(attempt: Int): Long {
        val time = initialTime * base.pow(attempt - 1)

        return time.toLong()
    }
}
