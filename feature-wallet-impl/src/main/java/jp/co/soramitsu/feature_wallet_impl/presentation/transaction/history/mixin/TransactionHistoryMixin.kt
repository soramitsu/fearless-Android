package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface TransactionHistoryUi {

    sealed class State {

        object Empty : State()

        object EmptyProgress : State()

        class Data(val items: List<Any>) : State()
    }

    val state: Flow<State>

    fun transactionClicked(transactionModel: OperationParcelizeModel)
}

interface TransactionHistoryMixin : TransactionHistoryUi, CoroutineScope {

    suspend fun syncFirstOperationsPage(): Result<*>

    fun scrolled(currentIndex: Int)
}
