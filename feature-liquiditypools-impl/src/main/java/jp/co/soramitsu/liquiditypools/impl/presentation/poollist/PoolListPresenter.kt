package jp.co.soramitsu.liquiditypools.impl.presentation.poollist

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.presentation.toListItemState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.isFilterMatch
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

class PoolListPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
) : PoolListScreenInterface {

    private val enteredAssetQueryFlow = MutableStateFlow("")

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.ListPoolsScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Lazily, 1)

    val chainFlow = screenArgsFlow.map { screenArgs ->
        chainsRepository.getChain(screenArgs.chainId)
    }

    val pools = screenArgsFlow.flatMapLatest { screenArgs ->
        combine(
            flowOf { poolsInteractor.getBasicPools(screenArgs.chainId) },
            enteredAssetQueryFlow
        ) { pools, query ->
            pools.filter {
                it.isFilterMatch(query)
            }.sortedWith { o1, o2 ->
                compareNullDesc(o1.tvl, o2.tvl)
            }
        }.map {
            it.mapNotNull(BasicPoolData::toListItemState)
        }
    }


    init {

    }

    private val stateFlow = MutableStateFlow(PoolListState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<PoolListState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private fun subscribeState(coroutineScope: CoroutineScope) {
        pools.onEach {
            stateFlow.value = stateFlow.value.copy(pools = it)
        }.launchIn(coroutineScope)
    }


    override fun onPoolClicked(pair: StringPair) {
//        val xorPswap = Pair("b774c386-5cce-454a-a845-1ec0381538ec", "37a999a2-5e90-4448-8b0e-98d06ac8f9d4")
        val chainId = screenArgsFlow.replayCache.firstOrNull()?.chainId ?: return
        internalPoolsRouter.openDetailsPoolScreen(chainId, pair)
    }


    override fun onAssetSearchEntered(value: String) {
        enteredAssetQueryFlow.value = value
    }

    private fun jp.co.soramitsu.wallet.impl.domain.model.Asset.isMatchFilter(filter: String): Boolean =
        this.token.configuration.name?.lowercase()?.contains(filter.lowercase()) == true ||
                this.token.configuration.symbol.lowercase().contains(filter.lowercase()) ||
                this.token.configuration.id.lowercase().contains(filter.lowercase())


}