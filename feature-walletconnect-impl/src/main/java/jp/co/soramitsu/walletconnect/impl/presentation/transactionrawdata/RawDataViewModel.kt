package jp.co.soramitsu.walletconnect.impl.presentation.transactionrawdata

import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject

@HiltViewModel
class RawDataViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walletConnectRouter: WalletConnectRouter
) : RawDataScreenInterface, BaseViewModel() {

    private val initRawData: String = savedStateHandle[RawDataFragment.RAW_DATA_KEY] ?: ""

    val state: StateFlow<RawDataViewState> = flowOf(RawDataViewState(rawData = initRawData))
        .map {
            val formatted: String? = runCatching { JSONObject(it.rawData).toString(2) }.getOrNull()

            it.copy(rawData = formatted ?: it.rawData)
        }
        .stateIn(this, SharingStarted.Eagerly, RawDataViewState.default)

    override fun onClose() {
        walletConnectRouter.back()
    }
}