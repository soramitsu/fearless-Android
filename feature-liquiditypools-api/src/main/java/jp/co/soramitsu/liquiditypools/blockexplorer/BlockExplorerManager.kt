package jp.co.soramitsu.liquiditypools.blockexplorer

import android.util.Log
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class BlockExplorerManager @Inject constructor(
    private val info: BlockExplorerRepository,
) {

    private val coroutineContext: CoroutineContext = Dispatchers.Default
    private val coroutineScope =
        CoroutineScope(
            coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
                Log.e("BlockExplorerManager", throwable.message.orEmpty())
            }
        )

    private val apyDeferred = coroutineScope.async {
        info.getApy(
            if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId
        ).associate { it.id to it.value }
    }

    suspend fun syncSbApy() {
        apyDeferred.await()
    }

    suspend fun getApy(id: String): Double? {
        return apyDeferred.await()[id]?.toDoubleNan()?.times(100)
    }
}

private fun String.toDoubleNan(): Double? = this.toDoubleOrNull()?.let {
    if (it.isNaN()) null else it
}
