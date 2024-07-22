package jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.GradientIconData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class PoolDetailsPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
) : PoolDetailsCallbacks {

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.PoolDetailsScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)


    init {

    }

    private val stateFlow = MutableStateFlow(PoolDetailsState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<PoolDetailsState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private fun subscribeState(coroutineScope: CoroutineScope) {
        screenArgsFlow.onEach {
            requestPoolDetails(it.ids)?.let {
                stateFlow.value = it
            }
        }.launchIn(coroutineScope)
    }


    override fun onSupplyLiquidityClick() {
        coroutinesStore.ioScope.launch {
            screenArgsFlow.map {
                it.ids
            }.firstOrNull()?.let { ids ->
                internalPoolsRouter.openAddLiquidityScreen(ids)
            }
        }
    }

    override fun onRemoveLiquidityClick() {
        println("!!! onRemoveLiquidityClick")
    }

    suspend fun requestPoolDetails(ids: StringPair): PoolDetailsState? {
        val soraChain = accountInteractor.getChain(soraMainChainId)
        val address = accountInteractor.selectedMetaAccount().address(soraChain).orEmpty()
        val baseAsset = soraChain.assets.firstOrNull { it.id == ids.first }
        val targetAsset = soraChain.assets.firstOrNull { it.id == ids.second }
        val baseTokenId = baseAsset?.currencyId ?: error("No currency for Asset ${baseAsset?.symbol}")
        val targetTokenId = targetAsset?.currencyId ?: error("No currency for Asset ${targetAsset?.symbol}")

        val retur = poolsInteractor.getUserPoolData(address, baseTokenId, targetTokenId.fromHex())?.let {
            PoolDetailsState(
                originTokenIcon = GradientIconData(baseAsset.iconUrl, null),
                destinationTokenIcon = GradientIconData(targetAsset.iconUrl, null),
                fromTokenSymbol = baseAsset.symbol,
                toTokenSymbol = targetAsset.symbol,
                tvl = null,
                apy = null
            )
        }
        return retur
    }

    private fun jp.co.soramitsu.wallet.impl.domain.model.Asset.isMatchFilter(filter: String): Boolean =
        this.token.configuration.name?.lowercase()?.contains(filter.lowercase()) == true ||
                this.token.configuration.symbol.lowercase().contains(filter.lowercase()) ||
                this.token.configuration.id.lowercase().contains(filter.lowercase())


}