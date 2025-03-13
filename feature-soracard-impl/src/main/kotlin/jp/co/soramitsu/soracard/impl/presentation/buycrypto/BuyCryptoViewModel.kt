package jp.co.soramitsu.soracard.impl.presentation.buycrypto

import android.util.Base64
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BuyCryptoViewModel @Inject constructor(
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val soraCardInteractor: SoraCardInteractor,
) : BaseViewModel() {

    private val _state = MutableStateFlow(BuyCryptoState())
    val state = _state.asStateFlow()

    init {
        setUpScript()
    }

    fun onPageFinished() {
        _state.update {
            it.copy(
                loading = false
            )
        }
    }

    private fun setUpScript() {
        val payload = UUID.randomUUID().toString()
        viewModelScope.launch {
            val chainId = soraCardInteractor.soraCardChainId
            val address = currentAccountAddress(chainId) ?: return@launch

            val unEncodedHtml = "<html><head><meta name=\"color-scheme\" content=\"dark light\"></head><body>" +
                "<div id=\"${BuildConfig.X1_WIDGET_ID}\" data-address=\"${address}\" " +
                "data-from-currency=\"EUR\" data-from-amount=\"100\" data-hide-buy-more-button=\"true\" " +
                "data-hide-try-again-button=\"true\" data-locale=\"en\" data-payload=\"${payload}\"></div>" +
                "<script async src=\"${BuildConfig.X1_ENDPOINT_URL}\"></script>" +
                "</body></html>"
            val encodedHtml = Base64.encodeToString(unEncodedHtml.toByteArray(), Base64.NO_PADDING)

            _state.update {
                it.copy(
                    script = encodedHtml
                )
            }
        }
    }
}
