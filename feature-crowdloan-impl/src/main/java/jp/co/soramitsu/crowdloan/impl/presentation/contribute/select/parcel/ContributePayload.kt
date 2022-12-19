package jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel

import android.os.Parcelable
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize

@Parcelize
class ContributePayload(
    val chainId: ChainId,
    val paraId: ParaId,
    val parachainMetadata: ParachainMetadataParcelModel?
) : Parcelable
