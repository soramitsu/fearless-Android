package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.presentation.toListItemState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

class AllPoolsPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
) : AllPoolsScreenInterface {

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.AllPoolsScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)


//    val pools = flowOf { poolsInteractor.getBasicPools() }.map { pools ->
//        pools.sortedWith { o1, o2 ->
//            compareNullDesc(o1.tvl, o2.tvl)
//        }
//    }.map {
//        it.map(BasicPoolData::toListItemState)
//    }

    private val chainFlow = flowOf {
        chainsRepository.getChain(poolsInteractor.poolsChainId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPools = combine(
        accountInteractor.selectedMetaAccountFlow(),
        chainFlow
    ) { wallet, chain ->
        wallet.address(chain)
    }
        .mapNotNull { it }
        .distinctUntilChanged()
        .flatMapLatest { address ->
            println("!!! allPools flatMapLatest address = $address")
            poolsInteractor.subscribePoolsCacheOfAccount(address)
        }.map { pools ->
            println("!!! allPools subscribePoolsCacheOfAccount pools.size = ${pools.size}")
            pools.groupBy {
                it.user != null
            }.mapValues {
                it.value.sortedWith { o1, o2 ->
                    compareNullDesc(o1.basic.tvl, o2.basic.tvl)
                }
            }
        }.map {
            println("!!! allPools grouped users = ${it[true]?.size}; other = ${it[false]?.size}")
//            it.map(BasicPoolData::toListItemState)
            it.mapValues { it ->
                it.value.mapNotNull { it.basic.toListItemState() }
            }
        }

    init {

    }
    private val stateFlow = MutableStateFlow(AllPoolsState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<AllPoolsState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private fun subscribeState(coroutineScope: CoroutineScope) {
        allPools.onEach { poolLists ->
            println("!!! allPools subscribeState poolLists.size = ${poolLists.size}")

            val userPools = poolLists[true].orEmpty()
            val otherPools = poolLists[false].orEmpty()

            val shownUserPools = userPools.take(5)
            val shownOtherPools = otherPools.take(10)

            val hasExtraUserPools = shownUserPools.size < userPools.size
            val hasExtraAllPools = shownOtherPools.size < otherPools.size

            stateFlow.value = stateFlow.value.copy(
                userPools = shownUserPools,
                allPools = shownOtherPools,
                hasExtraUserPools = hasExtraUserPools,
                hasExtraAllPools = hasExtraAllPools,
                isLoading = false
            )
        }.launchIn(coroutineScope)
    }


    override fun onPoolClicked(pair: StringPair) {
        internalPoolsRouter.openDetailsPoolScreen(pair)
    }

    override fun onMoreClick(isUserPools: Boolean) {
        internalPoolsRouter.openPoolListScreen(isUserPools)
    }
}