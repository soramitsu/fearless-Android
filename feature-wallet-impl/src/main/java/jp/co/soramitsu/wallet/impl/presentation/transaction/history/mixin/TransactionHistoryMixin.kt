package jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin

import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransactionDetailsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface TransactionHistoryUi {

    sealed class State {

        class Empty(val message: String? = null) : State()

        object EmptyProgress : State()

        object Refreshing : State()

        class Data(val items: List<Any>) : State()
    }

    sealed class SideEffect {
        class Error(val message: String?) : SideEffect()
    }

    fun state(): Flow<State>

    fun sideEffects(): Flow<SideEffect>

    suspend fun getTransactionDetailsState(
        transactionModel: OperationModel,
        assetPayload: AssetPayload
    ): TransactionDetailsState
}

interface TransactionHistoryMixin : TransactionHistoryUi, CoroutineScope {

    suspend fun syncFirstOperationsPage(assetPayload: AssetPayload)

    fun scrolled(currentIndex: Int, assetPayload: AssetPayload)
}
