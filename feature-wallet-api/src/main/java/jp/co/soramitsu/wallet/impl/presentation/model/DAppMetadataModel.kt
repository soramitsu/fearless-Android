package jp.co.soramitsu.wallet.impl.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class DAppMetadataModel(
    val url: String?,
    val address: String,
    val icon: String?,
    val name: String
) : Parcelable