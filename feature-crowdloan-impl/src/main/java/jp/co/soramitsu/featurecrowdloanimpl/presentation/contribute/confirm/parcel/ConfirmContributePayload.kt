package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.confirm.parcel

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.acala.AcalaContributionType
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.acala.AcalaContributionType.DirectDOT
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.select.parcel.ParachainMetadataParcelModel
import kotlinx.android.parcel.Parcelize

@Parcelize
class ConfirmContributePayload(
    val paraId: ParaId,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val bonusPayload: BonusPayload?,
    val metadata: ParachainMetadataParcelModel?,
    val estimatedRewardDisplay: String?,
    val enteredEtheriumAddress: Pair<String, Boolean>?,
    val signature: String?,
    var contributionType: AcalaContributionType = DirectDOT
) : Parcelable
