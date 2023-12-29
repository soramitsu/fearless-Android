package co.jp.soramitsu.walletconnect.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ChainChooseState(
    val items: List<String>,
    val selected: List<String>,
    val isViewMode: Boolean = false
) : Parcelable

@Parcelize
data class ChainChooseResult(
    val selectedChainIds: Set<String>
) : Parcelable
