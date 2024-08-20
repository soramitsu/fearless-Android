package jp.co.soramitsu.liquiditypools.impl.presentation.poollist

import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.isFilterMatch
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.presentation.toListItemState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class PoolListPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val poolsInteractor: PoolsInteractor
) : PoolListScreenInterface {

    private val enteredAssetQueryFlow = MutableStateFlow("")

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.ListPoolsScreen>()
        .onEach {
            stateFlow.value = stateFlow.value.copy(isLoading = true)
        }
        .shareIn(coroutinesStore.uiScope, SharingStarted.Lazily, 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pools = screenArgsFlow.flatMapLatest { screenArgs ->
        poolsInteractor.subscribePoolsCacheCurrentAccount().map { pools ->
            pools.asSequence().filter {
                if (screenArgs.isUserPools) {
                    it.user != null
                } else {
                    true
                }
            }.toList()
        }
    }

    private val poolsStates = combine(
            pools,
            enteredAssetQueryFlow
        ) { pools, query ->
            coroutineScope {
                val tokensDeferred =
                    pools.map { async { walletInteractor.getToken(it.basic.baseToken) } }
                val tokensMap = tokensDeferred.awaitAll().associateBy { it.configuration.id }

                pools.filter {
                    it.basic.isFilterMatch(query)
                }.sortedWith { current, next ->
                    val currentTokenFiatRate = tokensMap[current.basic.baseToken.id]?.fiatRate
                    val nextTokenFiatRate = tokensMap[next.basic.baseToken.id]?.fiatRate
                    val userPoolData = current.user
                    val userPoolNextData = next.user

                    if (userPoolData != null && userPoolNextData != null) {
                        val currentPooled = userPoolData.basePooled.applyFiatRate(currentTokenFiatRate)
                        val nextPooled = userPoolNextData.basePooled.applyFiatRate(nextTokenFiatRate)
                        compareNullDesc(currentPooled, nextPooled)
                    } else {
                        val currentTvl = current.basic.getTvl(currentTokenFiatRate)
                        val nextTvl = next.basic.getTvl(nextTokenFiatRate)
                        compareNullDesc(currentTvl, nextTvl)
                    }
                }.mapNotNull { it.toListItemState(tokensMap[it.basic.baseToken.id]) }
            }
        }

    private val stateFlow = MutableStateFlow(PoolListState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<PoolListState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private fun subscribeState(coroutineScope: CoroutineScope) {
        poolsStates.onEach {
            stateFlow.value = stateFlow.value.copy(pools = it, isLoading = false)
        }.launchIn(coroutineScope)

        enteredAssetQueryFlow.onEach {
            stateFlow.value = stateFlow.value.copy(searchQuery = it)
        }.launchIn(coroutineScope)

        pools.onEach { commonPoolData: List<CommonPoolData> ->
            val apyMap = coroutineScope.async {
                commonPoolData.mapNotNull { pool ->
                    val baseTokenId = pool.basic.baseToken.currencyId ?: return@mapNotNull null
                    val targetTokenId = pool.basic.targetToken?.currencyId ?: return@mapNotNull null
                    val id = StringPair(baseTokenId, targetTokenId)
                    val sbApy = poolsInteractor.getSbApy(pool.basic.reserveAccount)
                    id to sbApy
                }.toMap()
            }
            apyMap.join()
            stateFlow.update { prevState ->
                val newPools = prevState.pools.map { pool ->
                    apyMap.await()[pool.ids]?.let { sbApy ->
                        pool.copy(
                            apy = LoadingState.Loaded(
                                sbApy.let { apy ->
                            "%s%%".format(apy.toBigDecimal().formatCrypto())
                        }
                            )
                        )
                    } ?: pool
                }

                prevState.copy(pools = newPools)
            }
        }.launchIn(coroutineScope)
    }

    override fun onPoolClicked(pair: StringPair) {
        internalPoolsRouter.openDetailsPoolScreen(pair)
    }

    override fun onAssetSearchEntered(value: String) {
        enteredAssetQueryFlow.value = value
    }
}
