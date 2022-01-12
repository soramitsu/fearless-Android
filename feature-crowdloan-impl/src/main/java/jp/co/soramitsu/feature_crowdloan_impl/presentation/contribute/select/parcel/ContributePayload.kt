package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel

import android.os.Parcelable
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ContributePayload(
    val chainId: ChainId,
    val paraId: ParaId,
    val parachainMetadata: ParachainMetadataParcelModel?
) : Parcelable
