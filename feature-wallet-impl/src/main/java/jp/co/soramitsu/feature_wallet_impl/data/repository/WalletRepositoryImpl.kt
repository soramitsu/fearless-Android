package jp.co.soramitsu.feature_wallet_impl.data.repository

import io.reactivex.Observable
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.data.network.source.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.data
import java.math.BigDecimal

class WalletRepositoryImpl(
    private val substrateSource: WssSubstrateSource,
    private val accountRepository: AccountRepository
) : WalletRepository {

    override fun getAssets(): Observable<List<Asset>> {
        return accountRepository.observeSelectedAccount().map { account ->
            val node = accountRepository.getSelectedNode().blockingGet()
            val accountInfo = substrateSource.fetchAccountInfo(account, node).blockingGet()

            listOf(createAsset(node.networkType, accountInfo))
        }
    }

    private fun createAsset(networkType: Node.NetworkType, accountInfo: EncodableStruct<AccountInfo>): Asset {
        val balanceInPlanks = accountInfo[data][free]

        return Asset(
            Asset.Token.fromNetworkType(networkType),
            balanceInPlanks,
            BigDecimal(10.0),
            BigDecimal(10.0)
        )
    }
}