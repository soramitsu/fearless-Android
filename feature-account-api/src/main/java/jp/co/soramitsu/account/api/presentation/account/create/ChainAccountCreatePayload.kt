package jp.co.soramitsu.account.api.presentation.account.create

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize

@Parcelize
class ChainAccountCreatePayload(
    val chainId: ChainId,
    val metaId: Long,
    val isImport: Boolean
) : Parcelable
