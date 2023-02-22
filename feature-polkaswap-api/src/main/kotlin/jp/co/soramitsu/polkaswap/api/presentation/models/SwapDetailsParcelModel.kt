package jp.co.soramitsu.polkaswap.api.presentation.models

import android.os.Parcelable
import java.math.BigInteger
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import kotlinx.parcelize.Parcelize

@Parcelize
data class SwapDetailsParcelModel(
    val amount: BigInteger,
    val selectedMarket: Market,
    val desired: WithDesired,
    val dexId: Int,
    val minMax: BigInteger?,
    val networkFee: SwapDetailsViewState.NetworkFee
) : Parcelable
