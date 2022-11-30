package jp.co.soramitsu.account.impl.presentation.exporting.json.confirm

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize

@Parcelize
class ExportJsonConfirmPayload(
    val metaId: Long,
    val chainId: ChainId,
    val substrateJson: String?,
    val ethereumJson: String?,
    val isExportWallet: Boolean
) : Parcelable
