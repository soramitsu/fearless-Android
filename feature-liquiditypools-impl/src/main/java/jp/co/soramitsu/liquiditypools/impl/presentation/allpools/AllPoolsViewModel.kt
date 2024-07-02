package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsRouter
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@HiltViewModel
class AllPoolsViewModel @Inject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val poolsRouter: LiquidityPoolsRouter
) : BaseViewModel(), AllPoolsScreenInterface {

    private val _state = MutableStateFlow(AllPoolsState())
    val pools = flowOf {
        poolsInteractor.getBasicPools().sortedWith { o1, o2 ->
            compareNullDesc(o1.tvl, o2.tvl)
        }
    }.map {
        it.map(BasicPoolData::toListItemState)
    }.share()

    val state = _state.asStateFlow()

    init {
        subscribeScreenState()
    }

    private fun subscribeScreenState() {
        pools.onEach {
            _state.value = _state.value.copy(pools = it)
        }.launchIn(this)
    }

    override fun onPoolClicked(pair: StringPair) {
        println("!!! CLIKED ON PoolPair: $pair")
    }

    override fun onNavigationClick() {
        println("!!! CLIKED onNavigationClick")
        exitFlow()
    }
    override fun onCloseClick() {
        println("!!! CLIKED onCloseClick")
        exitFlow()
    }

    override fun onMoreClick() {
        println("!!! CLIKED onMoreClick")


    }

    fun exitFlow() {
        poolsRouter.back()
    }
}

private fun BasicPoolData.toListItemState(): BasicPoolListItemState {
    val tvl = this.baseToken.token.fiatRate?.times(BigDecimal(2))
        ?.multiply(this.baseReserves)

    return BasicPoolListItemState(
        ids = StringPair(this.baseToken.token.configuration.id, this.targetToken?.id.orEmpty()),  // todo
        token1Icon = this.baseToken.token.configuration.iconUrl,
        token2Icon = this.targetToken?.iconUrl.orEmpty(),
        text1 = "${this.baseToken.token.configuration.symbol}-${this.targetToken?.symbol}",
        text2 = tvl?.formatFiat().orEmpty(),
        text3 = this.sbapy?.let {
            "%s%%".format(it.toBigDecimal().formatCrypto())
        }.orEmpty(),
    )
}
