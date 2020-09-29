package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin.TransactionFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin.TransactionHistoryUi
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin.TransferHistoryMixin
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
    private val transferHistoryMixin: TransferHistoryMixin
) : BaseViewModel(), TransactionHistoryUi by transferHistoryMixin {
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
        disposables += transferHistoryMixin.transferHistoryDisposable

        transferHistoryMixin.setTransactionErrorHandler(errorHandler)

        transferHistoryMixin.setTransactionSyncedInterceptor { transactionsRefreshFinished() }

        transferHistoryMixin.addFilter(TokenFilter(token))
    }

    override fun onCleared() {
        super.onCleared()

        transferHistoryMixin.clear()
    }

//    val balanceLiveData = getBalance().asLiveData()

//    private fun getBalance(): Observable<BalanceModel> {
//        return interactor.getAssets()
//            .subscribeOn(Schedulers.io())
//            .map { it.map(Asset::toUiModel) }
//            .map(::BalanceModel)
//            .observeOn(AndroidSchedulers.mainThread())
//    }

    fun syncAssets() {
//        disposables += interactor.syncAssets()
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .doOnComplete { balanceRefreshFinished() }
//            .subscribeToError(errorHandler)
    }

    fun refresh() {
        transactionsRefreshed = false
        balanceRefreshed = false

        syncAssets()
        syncFirstTransactionsPage()

        balanceRefreshFinished()
    }

    fun backClicked() {
        router.back()
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