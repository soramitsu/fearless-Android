package jp.co.soramitsu.feature_account_impl.presentation.exporting.seed

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ExportSeedPayload(
    val metaId: Long,
    val chainId: ChainId
) : Parcelable
