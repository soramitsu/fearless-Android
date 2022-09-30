package jp.co.soramitsu.account.impl.presentation.node.details

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class NodeDetailsPayload(val chainId: String, val nodeUrl: String) : Parcelable
