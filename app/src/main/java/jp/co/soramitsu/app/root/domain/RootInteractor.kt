package jp.co.soramitsu.app.root.domain

import java.util.concurrent.CancellationException
import jp.co.soramitsu.wallet.impl.data.buyToken.ExternalProvider
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootInteractor(
) {
    fun isBuyProviderRedirectLink(link: String) = ExternalProvider.REDIRECT_URL_BASE in link

    suspend fun getPendingListOfSessionRequests(topic: String) = withContext(Dispatchers.Default) {
        try {
            WCDelegate.getPendingListOfSessionRequests(topic)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            error.printStackTrace()
            emptyList()
        }
    }
}
