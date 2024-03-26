package jp.co.soramitsu.account.api.presentation.actions

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize

@Parcelize
data class AddAccountPayload(
    val metaId: Long,
    val chainId: ChainId,
    val chainName: String,
    val assetId: String,
    val markedAsNotNeed: Boolean
) : Parcelable