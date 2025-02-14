package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface ChainSelectScreenContract {

    interface State {

        val chains: List<ItemState>?
        val selectedChainId: ChainId?
        val searchQuery: String?
        val showAllChains: Boolean

        data class Impl(
            override val chains: List<ItemState>?,
            override val selectedChainId: ChainId?,
            override val searchQuery: String? = null,
            override val showAllChains: Boolean = true
        ): State {
            companion object {
                val default = Impl(null, null)
            }

            data class FilteringDecorator(
                val appliedFilter: ChainSelectorViewStateWithFilters.Filter,
                val selectedFilter: ChainSelectorViewStateWithFilters.Filter,
                private val state: State,
            ): State by state
        }

        interface ItemState {

            val id: String
            val imageUrl: String?
            val title: String
            val isSelected: Boolean
            val tokenSymbols: Map<String, String>

            fun markSelected(isSelected: Boolean): ItemState

            data class Impl(
                override val id: String,
                override val imageUrl: String?,
                override val title: String,
                override val isSelected: Boolean = false,
                override val tokenSymbols: Map<String, String> = mapOf()
            ): ItemState {

                override fun markSelected(isSelected: Boolean): ItemState {
                    return copy(isSelected = isSelected)
                }

                data class FilteringDecorator(
                    val isMarkedAsFavorite: Boolean = false,
                    private val itemState: ItemState
                ): ItemState by itemState {
                    override fun markSelected(isSelected: Boolean): ItemState {
                        return this.copy(itemState = itemState.markSelected(isSelected))
                    }
                }

            }
        }

    }

    fun onBackButtonClick()

    fun onFilterApplied(filter: ChainSelectorViewStateWithFilters.Filter)

    fun onChainMarkedFavorite(chainItemState: State.ItemState)

    fun onChainSelected(chainItemState: State.ItemState?)

    fun onSearchInput(input: String)

    fun onDialogClose()

}

fun Chain.toChainItemState() = ChainSelectScreenContract.State.ItemState.Impl(
    id = id,
    imageUrl = icon,
    title = name,
    isSelected = false,
    tokenSymbols = assets.associate { it.id to it.symbol }
)

fun ChainSelectScreenContract.State.ItemState.Impl.toFilteredDecorator(
    isMarkedAsFavorite: Boolean
) = ChainSelectScreenContract.State.ItemState.Impl.FilteringDecorator(
    isMarkedAsFavorite = isMarkedAsFavorite,
    itemState = this
)