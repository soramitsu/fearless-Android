package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.model

import android.os.Parcelable
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.select.parcel.ParachainMetadataParcelModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class CustomContributePayload(
    val chainId: ChainId,
    val paraId: ParaId,
    val parachainMetadata: ParachainMetadataParcelModel,
    val amount: BigDecimal,
    val previousBonusPayload: BonusPayload?,
    val step: MoonbeamCrowdloanStep = TERMS, // used for moonbeam custom flow
    val isPrivacyAccepted: Boolean? = null
) : Parcelable
