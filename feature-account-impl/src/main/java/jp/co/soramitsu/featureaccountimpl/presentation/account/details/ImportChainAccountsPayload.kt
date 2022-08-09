package jp.co.soramitsu.featureaccountimpl.presentation.account.details

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ImportChainAccountsPayload(
    val chainId: ChainId,
    val metaId: Long,
    val chainName: String? = ""
) : Parcelable
