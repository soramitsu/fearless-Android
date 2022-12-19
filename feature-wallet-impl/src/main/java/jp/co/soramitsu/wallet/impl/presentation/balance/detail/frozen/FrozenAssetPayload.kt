package jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class FrozenAssetPayload(
    val locked: BigDecimal? = null,
    val staked: BigDecimal? = null,
    val reserved: BigDecimal? = null,
    val redeemable: BigDecimal? = null,
    val unstaking: BigDecimal? = null,
    val assetSymbol: String
) : Parcelable
