package jp.co.soramitsu.liquiditypools.blockexplorer

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

@Singleton
class BlockExplorerManager @Inject constructor(private val info: SoraWalletBlockExplorerInfo) {

    private val coroutineContext: CoroutineContext = Dispatchers.Default
    private val coroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e("BlockExplorerManager", throwable.message.orEmpty())
        })

    private val apyDeferred = coroutineScope.async { info.getSpApy().associate { it.id to it.sbApy } }

    suspend fun syncSbApy() {
        apyDeferred.await()
    }

    suspend fun getApy(id: String): Double? {
        return apyDeferred.await()[id]?.times(100)
    }
}