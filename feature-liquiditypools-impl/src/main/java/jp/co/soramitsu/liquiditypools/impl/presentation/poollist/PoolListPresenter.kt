package jp.co.soramitsu.liquiditypools.impl.presentation.poollist

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
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

    val pools = screenArgsFlow.flatMapLatest { screenArgs ->
        combine(
            poolsInteractor.subscribePoolsCacheCurrentAccount(),
            enteredAssetQueryFlow
        ) { pools, query ->
            pools.filter {
                if (screenArgs.isUserPools) {
                    it.user != null
                } else {
                    true
                }
            }.filter {
                it.basic.isFilterMatch(query)
            }.sortedWith { o1, o2 ->
                compareNullDesc(o1.basic.tvl, o2.basic.tvl)
            }
        }.map {
            it.mapNotNull{ it.basic.toListItemState() }
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

        enteredAssetQueryFlow.onEach {
            stateFlow.value = stateFlow.value.copy(searchQuery = it)
        }.launchIn(coroutineScope)
    }

    override fun onPoolClicked(pair: StringPair) {
        val chainId = screenArgsFlow.replayCache.firstOrNull()?.chainId ?: return
        internalPoolsRouter.openDetailsPoolScreen(chainId, pair)
    }

    override fun onAssetSearchEntered(value: String) {
        enteredAssetQueryFlow.value = value
    }
}