package jp.co.soramitsu.soracard.impl.presentation.buycrypto

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.soracard.api.domain.BuyCryptoRepository
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import jp.co.soramitsu.soracard.api.presentation.models.PaymentOrder
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BuyCryptoViewModel @Inject constructor(
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val buyCryptoRepository: BuyCryptoRepository,
    private val soraCardRouter: SoraCardRouter,
    private val soraCardInteractor: SoraCardInteractor
) : BaseViewModel() {

    var state by mutableStateOf(BuyCryptoState())
        private set

    init {
        setUpScript()
    }

    fun onPageFinished() {
        state = state.copy(loading = false)
    }

    private fun setUpScript() {
        val payload = UUID.randomUUID().toString()
        viewModelScope.launch {
            val chainId = soraCardInteractor.soraCardChainId
            val address = currentAccountAddress(chainId) ?: return@launch

            val unencodedHtml = "<html><head><meta name=\"color-scheme\" content=\"dark light\"></head><body>" +
                "<div id=\"${BuildConfig.X1_WIDGET_ID}\" data-address=\"${address}\" " +
                "data-from-currency=\"EUR\" data-from-amount=\"100\" data-hide-buy-more-button=\"true\" " +
                "data-hide-try-again-button=\"true\" data-locale=\"en\" data-payload=\"${payload}\"></div>" +
                "<script async src=\"${BuildConfig.X1_ENDPOINT_URL}\"></script>" +
                "</body></html>"
            val encodedHtml = Base64.encodeToString(unencodedHtml.toByteArray(), Base64.NO_PADDING)

            state = state.copy(script = encodedHtml)

            buyCryptoRepository.requestPaymentOrderStatus(PaymentOrder(paymentId = payload))
        }

        buyCryptoRepository.subscribePaymentOrderInfo()
            .onEach {
                if (it.paymentId == payload && it.depositTransactionStatus == "completed") {
                    soraCardRouter.back()
                }
            }
            .launchIn(viewModelScope)
    }
}
