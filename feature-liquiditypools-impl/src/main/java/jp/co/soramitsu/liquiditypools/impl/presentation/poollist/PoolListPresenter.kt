package jp.co.soramitsu.liquiditypools.impl.presentation.poollist

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.presentation.toListItemState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.polkaswap.api.domain.models.isFilterMatch
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

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
        .onEach {
            stateFlow.value = stateFlow.value.copy(isLoading = true)
        }
        .shareIn(coroutinesStore.uiScope, SharingStarted.Lazily, 1)

    private val pools = screenArgsFlow.flatMapLatest { screenArgs ->
        combine(
            poolsInteractor.subscribePoolsCacheCurrentAccount(),
            enteredAssetQueryFlow
        ) { pools, query ->
            coroutineScope {

                val tokensDeferred =
                    pools.map { async { walletInteractor.getToken(it.basic.baseToken) } }
                val tokensMap = tokensDeferred.awaitAll().associateBy { it.configuration.id }

                pools.filter {
                    if (screenArgs.isUserPools) {
                        it.user != null
                    } else {
                        true
                    }
                }.filter {
                    it.basic.isFilterMatch(query)
                }.sortedWith { current, next ->
                    val currentTvl = current.basic.getTvl(tokensMap[current.basic.baseToken.id]?.fiatRate)
                    val nextTvl = next.basic.getTvl(tokensMap[next.basic.baseToken.id]?.fiatRate)
                    compareNullDesc(currentTvl, nextTvl)
                }.mapNotNull { it.toListItemState(tokensMap[it.basic.baseToken.id]) }
            }
        }
    }

    private val stateFlow = MutableStateFlow(PoolListState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<PoolListState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private fun subscribeState(coroutineScope: CoroutineScope) {
        pools.onEach {
            stateFlow.value = stateFlow.value.copy(pools = it, isLoading = false)
        }.launchIn(coroutineScope)

        enteredAssetQueryFlow.onEach {
            stateFlow.value = stateFlow.value.copy(searchQuery = it)
        }.launchIn(coroutineScope)
    }

    override fun onPoolClicked(pair: StringPair) {
        internalPoolsRouter.openDetailsPoolScreen(pair)
    }

    override fun onAssetSearchEntered(value: String) {
        enteredAssetQueryFlow.value = value
    }
}