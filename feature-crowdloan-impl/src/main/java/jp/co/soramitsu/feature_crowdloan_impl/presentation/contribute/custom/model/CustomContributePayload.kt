package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ParachainMetadataParcelModel
import kotlinx.android.parcel.Parcelize

@Parcelize
class CustomContributePayload(
    val paraId: ParaId,
    val parachainMetadata: ParachainMetadataParcelModel,
    val amount: BigDecimal,
    val previousBonusPayload: BonusPayload?,
    val step: MoonbeamCrowdloanStep = TERMS, // used for moonbeam custom flow
    val isPrivacyAccepted: Boolean? = null
) : Parcelable
