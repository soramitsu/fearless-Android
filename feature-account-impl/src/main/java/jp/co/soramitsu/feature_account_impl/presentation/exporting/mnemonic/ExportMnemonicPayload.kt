package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ExportMnemonicPayload(
    val metaId: Long,
    val chainId: ChainId
) : Parcelable
