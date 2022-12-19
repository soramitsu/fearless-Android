package jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class FrozenTokensViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    val state = MutableStateFlow(FrozenTokensContentViewState(savedStateHandle[FROZEN_ASSET_PAYLOAD]!!))
}
