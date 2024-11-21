package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.SignRequestEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

@HiltViewModel
class DappScreenViewModel @Inject constructor(
    private val interactor: TonConnectInteractor,
    private val tonConnectRouter: TonConnectRouter,
    private val accountRepository: AccountRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val dapp = savedStateHandle.get<DappModel>(DappScreenFragment.PAYLOAD_DAPP_KEY) ?: error("No required info provided")

    val state = MutableStateFlow(dapp)


    suspend fun openTonConnectScreenForResult(url: String, proofPayload: String?): JSONObject {
//        val clientId = hex(Security.randomBytes(16))
        return interactor.tonConnectAppWithResult(null, url, proofPayload)
    }

    suspend fun openTonSignRequest(method: String, signRequest: SignRequestEntity): JSONObject {
         tonConnectRouter.openTonSignRequest(dapp, method, signRequest)

        return JSONObject()
    }

//    fun backButtonPressed() {
//        interactor.backToProfileScreen()
//    }
}
