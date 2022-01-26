package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ExportJsonConfirmPayload(
    val metaId: Long,
    val chainId: ChainId,
    val json: String
) : Parcelable
