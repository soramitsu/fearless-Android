package jp.co.soramitsu.polkaswap.api.sorablockexplorer

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.sbapy.SbApyInfo

@Singleton
class BlockExplorerManager @Inject constructor(
    private val info: SoraWalletBlockExplorerInfo
)  {

    private val tempApy = mutableListOf<SbApyInfo>()

    fun getTempApy(id: String) = tempApy.find {
        it.id == id
    }?.sbApy?.times(100)

    suspend fun updatePoolsSbApy() {
        updateSbApyInternal()
    }

    private suspend fun updateSbApyInternal() {
        runCatching {
            val response = info.getSpApy()
            println("!!! call blockExplorerManager.updatePoolsSbApy() result size = ${response.size}")
            tempApy.clear()
            tempApy.addAll(response)
            println("!!! call blockExplorerManager.updatePoolsSbApy() result updated")
        }
    }

}