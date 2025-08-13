package jp.co.soramitsu.app.root.domain

import com.reown.walletkit.client.WalletKit
import jp.co.soramitsu.wallet.impl.data.buyToken.ExternalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootInteractor(
) {
    fun isBuyProviderRedirectLink(link: String) = ExternalProvider.REDIRECT_URL_BASE in link



    suspend fun getPendingListOfSessionRequests(topic: String) = withContext(Dispatchers.Default){ WalletKit.getPendingListOfSessionRequests(topic) }
}
