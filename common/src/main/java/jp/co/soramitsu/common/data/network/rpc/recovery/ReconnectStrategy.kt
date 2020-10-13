package jp.co.soramitsu.common.data.network.rpc.recovery

import kotlin.math.pow

interface ReconnectStrategy {
    fun getTimeForReconnect(attempt: Int): Long
}

class ConstantReconnectStrategy(private val step: Long) : ReconnectStrategy {
    override fun getTimeForReconnect(attempt: Int) = step
}

class LinearReconnectStrategy(private val step: Long) : ReconnectStrategy {
    override fun getTimeForReconnect(attempt: Int) = attempt * step
}

class ExponentialReconnectStrategy(private val initialTime: Long): ReconnectStrategy {
    override fun getTimeForReconnect(attempt: Int): Long {
        val time =  initialTime * Math.E.pow(attempt - 1)

        return time.toLong()
    }
}