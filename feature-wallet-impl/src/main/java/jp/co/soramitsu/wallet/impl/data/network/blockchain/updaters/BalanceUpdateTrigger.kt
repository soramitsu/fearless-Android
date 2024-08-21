package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import io.ktor.util.date.getTimeMillis
import jp.co.soramitsu.core.models.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

const val UPDATE_TIMEOUT = 30_000L

object BalanceUpdateTrigger {

    private var lastUpdateTimeMap = mutableMapOf<ChainId, Long?>()

    private val flow = MutableSharedFlow<ChainId?>()

    fun observe(): Flow<ChainId?> {
        return flow
    }

    suspend operator fun invoke(chainId: ChainId? = null, force: Boolean = false) {
        val currentTime = getTimeMillis()
        val chainLastUpdateTime = chainId?.let { lastUpdateTimeMap[chainId] }

        if (force.not() && chainLastUpdateTime != null && currentTime - chainLastUpdateTime <= UPDATE_TIMEOUT) {
            return
        }
        flow.emit(chainId)
        chainId?.let {
            lastUpdateTimeMap[chainId] = getTimeMillis()
        }
    }
}