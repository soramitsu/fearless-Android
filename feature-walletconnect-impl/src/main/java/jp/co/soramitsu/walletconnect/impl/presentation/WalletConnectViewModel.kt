package jp.co.soramitsu.walletconnect.impl.presentation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class WalletConnectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : WalletConnectScreenInterface, BaseViewModel() {

    private val content: String = savedStateHandle[WalletConnectFragment.CONTENT_KEY] ?: error("No content provided")

    val state = flowOf(Web3WalletViewState(content))
        .stateIn(this, SharingStarted.Eagerly, Web3WalletViewState.default)


    override fun onClose() {
    }
}
