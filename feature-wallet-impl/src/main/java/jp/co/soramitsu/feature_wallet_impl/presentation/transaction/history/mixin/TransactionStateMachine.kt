package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import androidx.annotation.StringRes
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.subquery.subQueryTransactionPageSize
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.HistoryNotSupportedException

object TransactionStateMachine {

    private const val SCROLL_OFFSET = subQueryTransactionPageSize / 2

    sealed class State(val filters: Set<TransactionFilter>) {

        interface WithData {
            val data: List<Operation>
        }

        class Empty(filters: Set<TransactionFilter>, @StringRes val message: Int? = null) : State(filters)

        class EmptyProgress(filters: Set<TransactionFilter>) : State(filters)

        class Data(
            val curPageNumber: Long,
            override val data: List<Operation>,
            filters: Set<TransactionFilter>,
        ) : State(filters), WithData

        class NewPageProgress(
            val curPageNumber: Long,
            override val data: List<Operation>,
            filters: Set<TransactionFilter>,
        ) : State(filters), WithData

        class FullData(
            override val data: List<Operation>,
            filters: Set<TransactionFilter>,
        ) : State(filters), WithData
    }

    sealed class Action {

        class Scrolled(val currentItemIndex: Int) : Action()

        data class NewPage(val newPage: CursorPage<Operation>, val loadedWith: Set<TransactionFilter>) : Action()

        data class PageError(val error: Throwable) : Action()

        class FiltersChanged(val filters: Set<TransactionFilter>) : Action()
    }

    sealed class SideEffect {

        data class LoadPage(
            val curPageNumber: Long,
            val filters: Set<TransactionFilter>,
        ) : SideEffect()

        data class ErrorEvent(val error: Throwable) : SideEffect()
    }

    fun transition(
        action: Action,
        state: State,
        sideEffectListener: (SideEffect) -> Unit,
    ): State =
        when (action) {

            is Action.Scrolled -> {
                when (state) {
                    is State.Data -> {
                        if (action.currentItemIndex >= state.data.size - SCROLL_OFFSET) {
                            sideEffectListener(SideEffect.LoadPage(state.curPageNumber + 1, state.filters))

                            State.NewPageProgress(state.curPageNumber + 1, state.data, state.filters)
                        } else {
                            state
                        }
                    }

                    else -> state
                }
            }

            is Action.NewPage -> {
                val page = action.newPage
                val curPageNumber = page.curPageNumber

                when (state) {
                    is State.EmptyProgress -> {
                        when {
                            action.loadedWith != state.filters -> state // not relevant anymore page has arrived, still loading
                            page.isEmpty() -> State.Empty(state.filters)
                            page.endReached -> State.FullData(page, state.filters)
                            else -> State.Data(curPageNumber, page, state.filters)
                        }
                    }

                    is State.NewPageProgress -> {
                        when {
                            page.isEmpty() -> State.FullData(state.data, state.filters)
                            page.endReached -> State.FullData(state.data + page, state.filters)
                            else -> State.Data(curPageNumber, state.data + page, state.filters)
                        }
                    }

                    else -> state
                }
            }

            is Action.PageError -> {
                sideEffectListener(SideEffect.ErrorEvent(action.error))
                val message = when (action.error) {
                    is HistoryNotSupportedException -> R.string.wallet_transaction_history_unsupported_message
                    else -> R.string.wallet_transaction_history_error_message
                }

                when (state) {
                    is State.EmptyProgress -> State.Empty(state.filters, message)
                    is State.NewPageProgress -> State.Data(state.curPageNumber, state.data, state.filters)
                    else -> state
                }
            }

            is Action.FiltersChanged -> {
                val newFilters = action.filters

                sideEffectListener(SideEffect.LoadPage(1, filters = newFilters))

                State.EmptyProgress(newFilters)
            }
        }
}
