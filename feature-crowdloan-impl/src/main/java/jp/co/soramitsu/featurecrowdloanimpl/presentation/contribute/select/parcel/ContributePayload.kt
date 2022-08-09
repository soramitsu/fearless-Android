package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.select.parcel

import android.os.Parcelable
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ContributePayload(
    val chainId: ChainId,
    val paraId: ParaId,
    val parachainMetadata: ParachainMetadataParcelModel?
) : Parcelable
