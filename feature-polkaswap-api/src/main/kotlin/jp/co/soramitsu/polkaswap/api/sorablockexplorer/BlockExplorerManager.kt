package jp.co.soramitsu.polkaswap.api.sorablockexplorer

import android.util.Log
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class BlockExplorerManager @Inject constructor(private val info: SoraWalletBlockExplorerInfo) {

    private val coroutineContext: CoroutineContext = Dispatchers.Default
    private val coroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            println("!!! updateSbApyInternal error% ${throwable.message}")
        })

    private val apyDeferred = coroutineScope.async { info.getSpApy().associate { it.id to it.sbApy } }

    suspend fun syncSbApy() {
//        apyDeferred.join()
        val apy = apyDeferred.await()
        Log.d("&&&", "synced sbApy: ${apy.size}")
    }

    suspend fun getApy(id: String): Double? {
        return apyDeferred.await()[id]?.times(100)
    }

//    private val tempApy = mutableListOf<SbApyInfo>()
//
//    fun getTempApy(id: String) = tempApy.find {
//        it.id == id
//    }?.sbApy?.times(100)
//
//    suspend fun updatePoolsSbApy() {
//        updateSbApyInternal()
//    }
//
//    private suspend fun updateSbApyInternal() {
//        runCatching {
//            val response = info.getSpApy()
//            println("!!! call blockExplorerManager.updatePoolsSbApy() result size = ${response.size}")
//            tempApy.clear()
//            tempApy.addAll(response)
//            println("!!! call blockExplorerManager.updatePoolsSbApy() result updated")
//        }.onFailure {
//            println("!!! updateSbApyInternal error% ${it.message}")
//            it.printStackTrace()
//        }
//    }

}