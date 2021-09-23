package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BalanceDetailViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val assetPayload: AssetPayload,
    private val buyMixin: BuyMixin.Presentation,
    private val transactionHistoryMixin: TransactionHistoryMixin,
) : BaseViewModel(),
    TransactionHistoryUi by transactionHistoryMixin,
    BuyMixin by buyMixin {

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val _showFrozenDetailsEvent = MutableLiveData<Event<AssetModel>>()
    val showFrozenDetailsEvent: LiveData<Event<AssetModel>> = _showFrozenDetailsEvent

    val assetLiveData = currentAssetFlow().asLiveData()

    val buyEnabled = buyMixin.isBuyEnabled(assetPayload.chainId, assetPayload.chainAssetId)

    override fun onCleared() {
        super.onCleared()

        transactionHistoryMixin.cancel()
    }

    fun transactionsScrolled(index: Int) {
        transactionHistoryMixin.scrolled(index)
    }

    fun filterClicked() {
        router.openFilter()
    }

    fun sync() {
        viewModelScope.launch {
            val deferredAssetSync = async { interactor.syncAssetsRates() }
            val deferredTransactionsSync = async { transactionHistoryMixin.syncFirstOperationsPage() }

            val results = awaitAll(deferredAssetSync, deferredTransactionsSync)

            val firstError = results.mapNotNull { it.exceptionOrNull() }
                .firstOrNull()

            firstError?.let(::showError)

            _hideRefreshEvent.value = Event(Unit)
        }
    }

    fun backClicked() {
        router.back()
    }

    fun sendClicked() {
        router.openChooseRecipient()
    }

    fun receiveClicked() {
        router.openReceive()
    }

    fun buyClicked() {
        viewModelScope.launch {
            buyMixin.buyClicked(assetPayload.chainId, assetPayload.chainAssetId)
        }
    }

    fun frozenInfoClicked() {
        assetLiveData.value?.let {
            _showFrozenDetailsEvent.value = Event(it)
        }
    }

    private fun currentAssetFlow(): Flow<AssetModel> {
        return interactor.assetFlow(assetPayload.chainId, assetPayload.chainAssetId)
            .map { mapAssetToAssetModel(it) }
    }
}
