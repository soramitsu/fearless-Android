package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.presentation.toListItemState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.polkaswap.api.domain.models.CommonPoolData
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private val chainDeferred = coroutinesStore.uiScope.async {
        chainsRepository.getChain(poolsInteractor.poolsChainId)
    }

    init {
        loadPoolData()
    }

    private fun loadPoolData() {
        coroutinesStore.ioScope.launch {
            val currentAccount = accountInteractor.selectedMetaAccount()
            val address = currentAccount.address(chainDeferred.await()) ?: return@launch

            poolsInteractor.subscribePoolsCacheOfAccount(address)
                .onEach { commonPoolData: List<CommonPoolData> ->
                    val allRequiredChainAssets =
                        (commonPoolData.map { it.basic.baseToken } + commonPoolData.mapNotNull { it.basic.targetToken }).toSet()

                    val tokensDeferred =
                        allRequiredChainAssets.filter { it.priceId != null }.map { walletInteractor.getToken(it) }

                    val tokensMap = tokensDeferred.associateBy { it.configuration.id }

                    val poolLists = commonPoolData.groupBy {
                        it.user != null
                    }.mapValues {
                        it.value.sortedWith { current, next ->
                            val currentTvl =
                                current.basic.getTvl(tokensMap[current.basic.baseToken.id]?.fiatRate)
                            val nextTvl =
                                next.basic.getTvl(tokensMap[next.basic.baseToken.id]?.fiatRate)
                            compareNullDesc(currentTvl, nextTvl)
                        }.mapNotNull { commonPoolData ->
                            val token = tokensMap[commonPoolData.basic.baseToken.id]
                            commonPoolData.toListItemState(token)
                        }
                    }
                    val userPools = poolLists[true].orEmpty()
                    val otherPools = poolLists[false].orEmpty()

                    val shownUserPools = userPools.take(5)
                    val shownOtherPools = otherPools.take(10)
                    val hasExtraUserPools = shownUserPools.size < userPools.size
                    val hasExtraAllPools = shownOtherPools.size < otherPools.size

                    stateFlow.update { prevState ->
                        prevState.copy(
                            userPools = shownUserPools,
                            allPools = shownOtherPools,
                            hasExtraUserPools = hasExtraUserPools,
                            hasExtraAllPools = hasExtraAllPools,
                            isLoading = false
                        )
                    }
                }.launchIn(coroutinesStore.uiScope)
        }
    }

    private val stateFlow = MutableStateFlow(AllPoolsState())

    fun createScreenStateFlow(): StateFlow<AllPoolsState> {
        return stateFlow
    }

    override fun onPoolClicked(pair: StringPair) {
        internalPoolsRouter.openDetailsPoolScreen(pair)
    }

    override fun onMoreClick(isUserPools: Boolean) {
        internalPoolsRouter.openPoolListScreen(isUserPools)
    }
}