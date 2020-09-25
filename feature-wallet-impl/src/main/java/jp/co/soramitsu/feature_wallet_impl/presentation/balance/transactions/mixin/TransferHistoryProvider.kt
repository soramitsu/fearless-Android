package jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.DayHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.toUI
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

private const val PAGE_SIZE = 20

class TransferHistoryProvider(private val walletInteractor: WalletInteractor) : TransferHistoryMixin {

    override val transferHistoryDisposable = CompositeDisposable()

    private val _transactionsLiveData: MutableLiveData<List<Any>> = MutableLiveData()
    override val transactionsLiveData: LiveData<List<Any>> = _transactionsLiveData

    private var currentTransactions: List<TransactionModel> = emptyList()

    private var currentPage: Int = -1
    private var isLoading = false

    init {
        loadTransactionPage()
    }

    private fun loadTransactionPage() {
        if (isLoading) return

        currentPage++
        isLoading = true

        transferHistoryDisposable += fake()
            .subscribeOn(Schedulers.io())
            .map { it.map(Transaction::toUI) }
            .map(::regroup)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsLiveData.value = it
                isLoading = false
            }, DEFAULT_ERROR_HANDLER)
    }

    private fun fake() = Single.fromCallable {
        (0..PAGE_SIZE).map {
            val timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(currentPage.toLong())
            val hash = currentPage * (PAGE_SIZE + 1) + it

            val address1 = "5DEwU2U97RnBHCpfwHMDfJC7pqAdfWaPFib9wiZcr2ephSfT"
            val address2 = "F2dMuaCik4Ackmo9hoMMV79ETtVNvKSZMVK5sue9q1syPrW"

            Transaction(
                hash.toString(), Asset.Token.KSM,
                address1,
                address2,
                BigDecimal.TEN,
                timestamp,
                it % 2 == 0
            )
        }
    }

    private fun regroup(newPage: List<TransactionModel>): List<Any> {
        val all = currentTransactions + newPage

        currentTransactions = all

        return all.groupBy { extractDay(it.date) }
            .map { (_, transactions) ->
                val millis = transactions.first().date

                val header = DayHeader(millis)

                listOf(header) + transactions
            }.flatten()
    }

    override fun shouldLoadPage() {
        loadTransactionPage()
    }

    private fun extractDay(millis: Long): Long {
        return TimeUnit.MILLISECONDS.toDays(millis)
    }
}