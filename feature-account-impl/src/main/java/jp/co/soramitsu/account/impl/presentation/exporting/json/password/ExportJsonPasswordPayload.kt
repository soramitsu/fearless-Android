package jp.co.soramitsu.account.impl.presentation.exporting.json.password

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize

@Parcelize
class ExportJsonPasswordPayload(
    val metaId: Long,
    val chainId: ChainId,
    val isExportWallet: Boolean
) : Parcelable
