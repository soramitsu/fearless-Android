package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import io.ktor.util.date.getTimeMillis
import jp.co.soramitsu.core.models.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

object BalanceUpdateTrigger {

    private var lastUpdateTime: Long? = null

    private val flow = MutableSharedFlow<ChainId?>()

    fun observe(): Flow<ChainId?> {
        return flow
    }

    suspend operator fun invoke(chainId: ChainId? = null, force: Boolean = false) {
        val currentTime = getTimeMillis()
        if (force.not() && lastUpdateTime != null && currentTime - lastUpdateTime!! <= 30_000L) return
        flow.emit(chainId)
        lastUpdateTime = getTimeMillis()
    }
}