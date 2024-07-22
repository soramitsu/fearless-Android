package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class AllPoolsPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
) : AllPoolsScreenInterface {

    private val _stateSlippage = MutableStateFlow(0.5)
    val stateSlippage = _stateSlippage.asStateFlow()


    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.AllPoolsScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)


    val pools = flowOf { poolsInteractor.getBasicPools() }.map { pools ->
        pools.sortedWith { o1, o2 ->
            compareNullDesc(o1.tvl, o2.tvl)
        }
    }.map {
        it.map(BasicPoolData::toListItemState)
    }

    init {

    }
    private val stateFlow = MutableStateFlow(AllPoolsState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<AllPoolsState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private fun subscribeState(coroutineScope: CoroutineScope) {
        pools.onEach {
            stateFlow.value = stateFlow.value.copy(pools = it)
        }.launchIn(coroutineScope)
    }


    override fun onPoolClicked(pair: StringPair) {
        val xorPswap = Pair("b774c386-5cce-454a-a845-1ec0381538ec", "37a999a2-5e90-4448-8b0e-98d06ac8f9d4")
        internalPoolsRouter.openDetailsPoolScreen(xorPswap)
    }

    override fun onMoreClick() {
        internalPoolsRouter.openPoolListScreen()
    }

    private fun jp.co.soramitsu.wallet.impl.domain.model.Asset.isMatchFilter(filter: String): Boolean =
        this.token.configuration.name?.lowercase()?.contains(filter.lowercase()) == true ||
                this.token.configuration.symbol.lowercase().contains(filter.lowercase()) ||
                this.token.configuration.id.lowercase().contains(filter.lowercase())


}