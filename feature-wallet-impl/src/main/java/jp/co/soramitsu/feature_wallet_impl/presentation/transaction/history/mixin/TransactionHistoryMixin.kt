package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import androidx.annotation.StringRes
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface TransactionHistoryUi {

    sealed class State {

        class Empty(@StringRes val message: Int? = null) : State()

        object EmptyProgress : State()

        class Data(val items: List<Any>) : State()
    }

    val state: Flow<State>

    fun transactionClicked(transactionModel: OperationModel)
}

interface TransactionHistoryMixin : TransactionHistoryUi, CoroutineScope {

    fun scrolled(currentIndex: Int)
}
