package jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.allFiltersIncluded
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload

object TransactionStateMachine {

    const val PAGE_SIZE = 100
    private const val SCROLL_OFFSET = PAGE_SIZE / 2

    sealed class State(
        val filters: Set<TransactionFilter>,
        val assetPayload: AssetPayload
    ) {

        interface WithData {
            val data: List<Operation>
        }

        class Empty(
            filters: Set<TransactionFilter>,
            val message: String? = null,
            assetPayload: AssetPayload
        ) : State(filters, assetPayload)

        class EmptyProgress(
            filters: Set<TransactionFilter>,
            assetPayload: AssetPayload
        ) : State(filters, assetPayload)

        class Data(
            val nextCursor: String,
            override val data: List<Operation>,
            filters: Set<TransactionFilter>,
            assetPayload: AssetPayload
        ) : State(filters, assetPayload), WithData

        class NewPageProgress(
            val nextCursor: String,
            override val data: List<Operation>,
            filters: Set<TransactionFilter>,
            assetPayload: AssetPayload
        ) : State(filters, assetPayload), WithData

        class FullData(
            override val data: List<Operation>,
            filters: Set<TransactionFilter>,
            assetPayload: AssetPayload
        ) : State(filters, assetPayload), WithData
    }

    sealed class Action {

        class Scrolled(val currentItemIndex: Int) : Action()

        data class CachePageArrived(val newPage: CursorPage<Operation>, val accountChanged: Boolean) : Action()

        data class NewPage(val newPage: CursorPage<Operation>, val loadedWith: Set<TransactionFilter>) : Action()

        data class PageError(val error: Throwable) : Action()

        class FiltersChanged(val filters: Set<TransactionFilter>) : Action()

        class AssetPayloadChanged(val assetPayload: AssetPayload) : Action()
    }

    sealed class SideEffect {

        data class LoadPage(
            val nextCursor: String?,
            val filters: Set<TransactionFilter>,
            val assetPayload: AssetPayload,
            val pageSize: Int = PAGE_SIZE
        ) : SideEffect()

        data class ErrorEvent(val error: Throwable) : SideEffect()

        data class TriggerCache(val assetPayload: AssetPayload) : SideEffect()
    }

    fun transition(
        action: Action,
        state: State,
        sideEffectListener: (SideEffect) -> Unit
    ): State =
        when (action) {
            is Action.CachePageArrived -> {
                val nextCursor = action.newPage.nextCursor

                when {
                    !canUseCache(using = state.filters) -> {
                        if (action.accountChanged) {
                            // trigger cold load for new account when not able to use cache
                            sideEffectListener(
                                SideEffect.LoadPage(
                                    nextCursor = null,
                                    filters = state.filters,
                                    assetPayload = state.assetPayload
                                )
                            )

                            State.EmptyProgress(state.filters, state.assetPayload)
                        } else {
                            // if account is the same - ignore new page, since cache is not used
                            state
                        }
                    }
                    action.newPage.isEmpty() -> State.Empty(
                        filters = state.filters,
                        assetPayload = state.assetPayload
                    )
                    nextCursor != null -> State.Data(nextCursor, action.newPage, state.filters, state.assetPayload)
                    else -> State.FullData(action.newPage, state.filters, state.assetPayload)
                }
            }

            is Action.Scrolled -> {
                when (state) {
                    is State.Data -> {
                        if (action.currentItemIndex >= state.data.size - SCROLL_OFFSET) {
                            sideEffectListener(
                                SideEffect.LoadPage(
                                    state.nextCursor,
                                    state.filters,
                                    assetPayload = state.assetPayload
                                )
                            )

                            State.NewPageProgress(state.nextCursor, state.data, state.filters, state.assetPayload)
                        } else {
                            state
                        }
                    }

                    else -> state
                }
            }

            is Action.NewPage -> {
                val page = action.newPage
                val nextCursor = page.nextCursor

                when (state) {
                    is State.EmptyProgress -> {
                        when {
                            action.loadedWith != state.filters -> state // not relevant anymore page has arrived, still loading
                            page.isEmpty() -> State.Empty(state.filters, assetPayload = state.assetPayload)
                            nextCursor == null -> State.FullData(page, state.filters, state.assetPayload)
                            else -> State.Data(nextCursor, page, state.filters, state.assetPayload)
                        }
                    }

                    is State.NewPageProgress -> {
                        when {
                            page.isEmpty() -> State.FullData(state.data, state.filters, state.assetPayload)
                            nextCursor == null -> State.FullData(state.data + page, state.filters, state.assetPayload)
                            else -> State.Data(nextCursor, state.data + page, state.filters, state.assetPayload)
                        }
                    }

                    else -> state
                }
            }

            is Action.PageError -> {
                sideEffectListener(SideEffect.ErrorEvent(action.error))

                when (state) {
                    is State.EmptyProgress -> State.Empty(filters = state.filters, assetPayload = state.assetPayload)
                    is State.NewPageProgress -> State.Data(state.nextCursor, state.data, state.filters, state.assetPayload)
                    else -> state
                }
            }

            is Action.FiltersChanged -> {
                val newFilters = action.filters

                if (canUseCache(using = newFilters)) {
                    sideEffectListener(SideEffect.TriggerCache(state.assetPayload))
                } else {
                    sideEffectListener(
                        SideEffect.LoadPage(
                            nextCursor = null,
                            filters = newFilters,
                            assetPayload = state.assetPayload
                        )
                    )
                }

                State.EmptyProgress(filters = newFilters, assetPayload = state.assetPayload)
            }

            is Action.AssetPayloadChanged -> {
                if (canUseCache(using = state.filters)) {
                    sideEffectListener(SideEffect.TriggerCache(action.assetPayload))
                } else {
                    sideEffectListener(
                        SideEffect.LoadPage(
                            nextCursor = null,
                            filters = state.filters,
                            assetPayload = action.assetPayload
                        )
                    )
                }

                State.EmptyProgress(
                    filters = state.filters,
                    assetPayload = action.assetPayload
                )
            }
        }

    private fun canUseCache(using: Set<TransactionFilter>) = using.allFiltersIncluded()
}
