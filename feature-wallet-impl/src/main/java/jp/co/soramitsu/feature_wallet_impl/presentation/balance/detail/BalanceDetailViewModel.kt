package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

// TODO use dp
private const val ICON_SIZE_IN_PX = 40

private class TokenFilter(private val token: Asset.Token) : TransactionFilter {
    override fun shouldInclude(model: TransactionModel): Boolean {
        return token == model.token
    }
}

class BalanceDetailViewModel(
    private val interactor: WalletInteractor,
    private val iconGenerator: IconGenerator,
    private val router: WalletRouter,
    private val token: Asset.Token,
    private val transactionHistoryMixin: TransactionHistoryMixin
) : BaseViewModel(), TransactionHistoryUi by transactionHistoryMixin {
    private var transactionsRefreshed: Boolean = false
    private var balanceRefreshed: Boolean = false

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val errorHandler: ErrorHandler = {
        showError(it.message!!)

        transactionsRefreshFinished()
        balanceRefreshFinished()
    }

    init {
        disposables += transactionHistoryMixin.transferHistoryDisposable

        transactionHistoryMixin.setTransactionErrorHandler(errorHandler)

        transactionHistoryMixin.setTransactionSyncedInterceptor { transactionsRefreshFinished() }

        transactionHistoryMixin.addFilter(TokenFilter(token))
    }

    override fun onCleared() {
        super.onCleared()

        transactionHistoryMixin.clear()
    }

    val assetLiveData = observeAssetModel().asLiveData()

    private fun observeAssetModel(): Observable<AssetModel> {
        return interactor.observeAsset(token)
            .subscribeOn(Schedulers.io())
            .map(::mapAssetToAssetModel)
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun syncAsset() {
        disposables += interactor.syncAsset(token)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { balanceRefreshFinished() }
            .subscribeToError(errorHandler)
    }

    fun refresh() {
        transactionsRefreshed = false
        balanceRefreshed = false

        syncAsset()
        syncFirstTransactionsPage()
    }

    fun backClicked() {
        router.back()
    }

    fun sendClicked() {
        router.openChooseRecipient()
    }

    private fun transactionsRefreshFinished() {
        transactionsRefreshed = true

        maybeHideRefresh()
    }

    private fun balanceRefreshFinished() {
        balanceRefreshed = true

        maybeHideRefresh()
    }

    private fun maybeHideRefresh() {
        if (transactionsRefreshed && balanceRefreshed) {
            _hideRefreshEvent.value = Event(Unit)
        }
    }
}