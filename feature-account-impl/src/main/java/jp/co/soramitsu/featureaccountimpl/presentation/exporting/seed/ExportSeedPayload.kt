package jp.co.soramitsu.featureaccountimpl.presentation.exporting.seed

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ExportSeedPayload(
    val metaId: Long,
    val chainId: ChainId,
    val isExportWallet: Boolean = false
) : Parcelable
