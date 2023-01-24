package jp.co.soramitsu.polkaswap.api.presentation.models

import android.os.Parcelable
import jp.co.soramitsu.polkaswap.api.models.Market
import kotlinx.parcelize.Parcelize

@Parcelize
data class TransactionSettingsModel(val market: Market, val slippageTolerance: Double) : Parcelable
