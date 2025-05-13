package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.presentation.toListItemState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    private val chainDeferred = coroutinesStore.uiScope.async {
        chainsRepository.getChain(poolsInteractor.poolsChainId)
    }

    private val refresh = MutableStateFlow(Event(Unit))
    private val screenHeight = MutableStateFlow(0.dp)
    private val groupHeight = MutableStateFlow(0.dp)

    private val stateFlow = MutableStateFlow(AllPoolsState())

    fun createScreenStateFlow(scope: CoroutineScope): StateFlow<AllPoolsState> {
        subscribeScreenState(scope)
        return stateFlow
    }

    private fun subscribeScreenState(scope: CoroutineScope) {
        scope.launch {
            refresh.onEach {
                poolsInteractor.syncPools()
            }.launchIn(scope)

            val currentAccount = accountInteractor.selectedMetaAccount()
            val address = currentAccount.address(chainDeferred.await()) ?: return@launch

            poolsInteractor.subscribePoolsCacheOfAccount(address)
                .onEach { commonPoolData: List<CommonPoolData> ->
                    val allRequiredChainAssets =
                        (commonPoolData.map { it.basic.baseToken } + commonPoolData.mapNotNull { it.basic.targetToken }).toSet()

                    val tokensDeferred =
                        allRequiredChainAssets.filter { it.priceId != null }
                            .map { walletInteractor.getToken(it) }

                    val tokensMap = tokensDeferred.associateBy { it.configuration.id }

                    val poolLists = commonPoolData.groupBy {
                        it.user != null
                    }.mapValues {
                        it.value.sortedWith { current, next ->
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
                        }.mapNotNull { commonPoolData ->
                            val token = tokensMap[commonPoolData.basic.baseToken.id]
                            commonPoolData.toListItemState(token)
                        }
                    }
                    val userPools = poolLists[true].orEmpty()
                    val otherPools = poolLists[false].orEmpty()

                    val screenHeight = screenHeight.value
                    val groupHeight = groupHeight.value
                    val itemHeight = 40.dp
                    val margin = 16.dp

                    val availableHeight = screenHeight - groupHeight - margin * 2 - if (userPools.isNotEmpty()) {
                        groupHeight + margin
                    } else {
                        0.dp
                    }

                    val totalItems = (availableHeight / (itemHeight + margin)).toInt()
                    val maxGroupItems = totalItems / 2

                    val shownUserPools = userPools.take(maxGroupItems)
                    val userGroupItems = shownUserPools.size
                    val shownOtherPools = otherPools.take(totalItems - userGroupItems)

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
                }
                .onEach { commonPoolData: List<CommonPoolData> ->
                    coroutineScope {
                        commonPoolData.forEach { pool ->
                            launch pool@{
                                val baseTokenId = pool.basic.baseToken.currencyId ?: return@pool
                                val targetTokenId =
                                    pool.basic.targetToken?.currencyId ?: return@pool
                                val id = StringPair(baseTokenId, targetTokenId)
                                val sbApy = poolsInteractor.getSbApy(pool.basic.reserveAccount)

                                stateFlow.update { prevState ->
                                    val newUserPools = prevState.userPools.map {
                                        if (it.ids == id) {
                                            it.copy(
                                                apy = LoadingState.Loaded(
                                                    sbApy?.let { apy ->
                                                "%s%%".format(apy.toBigDecimal().formatCrypto())
                                            }.orEmpty()
                                                )
                                            )
                                        } else {
                                            it
                                        }
                                    }

                                    val newAllPools = prevState.allPools.map {
                                        if (it.ids == id) {
                                            it.copy(
                                                apy = LoadingState.Loaded(
                                                    sbApy?.let { apy ->
                                                "%s%%".format(apy.toBigDecimal().formatCrypto())
                                            }.orEmpty()
                                                )
                                            )
                                        } else {
                                            it
                                        }
                                    }

                                    prevState.copy(
                                        userPools = newUserPools,
                                        allPools = newAllPools
                                    )
                                }
                            }
                        }
                    }
                }
                .launchIn(scope)
        }
    }

    override fun onPoolClicked(pair: StringPair) {
        internalPoolsRouter.openDetailsPoolScreen(pair)
    }

    override fun onMoreClick(isUserPools: Boolean) {
        internalPoolsRouter.openPoolListScreen(isUserPools)
    }

    override fun onRefresh() {
        refresh.value = Event(Unit)
    }

    override fun onWindowHeightChange(heightIs: Dp) {
        screenHeight.value = heightIs
    }

    override fun onHeaderHeightChange(heightIs: Dp) {
        groupHeight.value = heightIs
    }
}
