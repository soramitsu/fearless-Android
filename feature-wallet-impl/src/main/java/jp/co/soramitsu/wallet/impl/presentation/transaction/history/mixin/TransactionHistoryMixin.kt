package jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin

import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface TransactionHistoryUi {

    sealed class State {

        class Empty(val message: String? = null) : State()

        object EmptyProgress : State()

        class Data(val items: List<Any>) : State()
    }

    fun state(): Flow<State>

    fun transactionClicked(
        transactionModel: OperationModel,
        assetPayload: AssetPayload
    )
}

interface TransactionHistoryMixin : TransactionHistoryUi, CoroutineScope {

    suspend fun syncFirstOperationsPage(assetPayload: AssetPayload): Result<*>

    fun scrolled(currentIndex: Int, assetPayload: AssetPayload)
}
