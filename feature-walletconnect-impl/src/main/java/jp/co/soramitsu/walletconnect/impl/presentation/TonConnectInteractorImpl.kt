package jp.co.soramitsu.walletconnect.impl.presentation

import android.net.Uri
import android.util.ArrayMap
import androidx.core.net.toUri
import co.jp.soramitsu.walletconnect.domain.TonConnectInteractor
import co.jp.soramitsu.walletconnect.domain.TonConnectRouter
import co.jp.soramitsu.walletconnect.model.AppEntity
import co.jp.soramitsu.walletconnect.model.TonConnect
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class TonConnectInteractorImpl(
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val keypairProvider: KeypairProvider,
    private val tonApi: TonApi,
    private val tonConnectRouter: TonConnectRouter,
    ): TonConnectInteractor {

    override suspend fun getChain(): Chain {
        val tonChainId = "-239"
        return chainsRepository.getChain(tonChainId)
    }

    override suspend fun connectRemoteApp(pairingUri: String) {
//        val pairingUri = "tc://?v=2&id=cdce01cd3b57f5f4aed61abc265cb6671f459d18440faece7311ca02eb075000&r=%7B%22manifestUrl%22%3A%22https%3A%2F%2Fgist.githubusercontent.com%2F1IxI1%2Fd15922c552204bda4eff69c5c135c010%2Fraw%2F791202048994565640de5555ec6c4bc2b03d45b9%2Fmanifest.json%22%2C%22items%22%3A%5B%7B%22name%22%3A%22ton_addr%22%7D%5D%7D&ret=none"
        val tonChain = getChain()

        val account = accountRepository.getSelectedMetaAccount()
        val accountId = account.tonPublicKey
            // ?.tonAccountId()
            ?: return

//        val keypair = withContext(Dispatchers.IO) { keypairProvider.getKeypairFor(tonChain, accountId) }

//        val tonConnect = TonConnect.parse(
//            uri = normalizedUri,
//            refSource = refSource,
//            fromQR = fromQR,
//            returnUri = returnUri
//        )

        val uri = kotlin.runCatching { Uri.parse(pairingUri) }.getOrNull() ?: return
        val returnUri = kotlin.runCatching { uri.getQueryParameter("ret")?.toUri() }.getOrNull()

        val tonConnect = TonConnect.parse(
            uri = uri,
            refSource = null,
            fromQR = true,
            returnUri = returnUri
        )

        if (tonConnect.request.items.isEmpty()) {
            println("!!! Bad request")
        }

        val app = readManifest(tonConnect.request.manifestUrl)

        println("!!! got dApp result: $app")
        tonConnectRouter.openTonConnectionDetails(app)
    }

    private suspend fun readManifest(url: String): AppEntity {
        val headers = ArrayMap<String, String>().apply {
            set("Connection", "close")
        }
        val response = tonApi.getManifest(url)
//        if (response.code != 200) {
//            throw ManifestException.NotFound(response.code)
//        }
//        val body = response.body?.string() ?: throw ManifestException.FailedParse(NullPointerException())
        return AppEntity(
            response.url,
            response.name,
            response.iconUrl,
            response.termsOfUseUrl,
            response.privacyPolicyUrl
        )

//        return try {
//            AppEntity(body)
//        } catch (e: Throwable) {
//            throw ManifestException.FailedParse(e)
//        }
    }



}