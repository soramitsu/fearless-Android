package jp.co.soramitsu.feature_account_api.presentation.account.create

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ChainAccountCreatePayload(
    val chainId: ChainId,
    val metaId: Long,
    val isImport: Boolean
) : Parcelable
