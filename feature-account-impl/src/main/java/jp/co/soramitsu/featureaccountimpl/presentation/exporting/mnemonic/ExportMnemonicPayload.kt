package jp.co.soramitsu.featureaccountimpl.presentation.exporting.mnemonic

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ExportMnemonicPayload(
    val metaId: Long,
    val chainId: ChainId,
    val isExportWallet: Boolean
) : Parcelable
