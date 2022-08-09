package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface TransactionHistoryUi {

    sealed class State {

        class Empty(val message: String? = null) : State()

        object EmptyProgress : State()

        class Data(val items: List<Any>) : State()
    }

    val state: Flow<State>

    fun transactionClicked(transactionModel: OperationModel)
}

interface TransactionHistoryMixin : TransactionHistoryUi, CoroutineScope {

    suspend fun syncFirstOperationsPage(): Result<*>

    fun scrolled(currentIndex: Int)

    fun setAssetPayload(asset: AssetPayload)
}
