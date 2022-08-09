package jp.co.soramitsu.featurecrowdloanimpl.presentation

import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.select.parcel.ContributePayload
import kotlinx.coroutines.flow.Flow

interface CrowdloanRouter {

    fun openContribute(payload: ContributePayload)

    val customBonusFlow: Flow<BonusPayload?>

    val latestCustomBonus: BonusPayload?

    fun openMoonbeamContribute(payload: CustomContributePayload)

    fun openMoonbeamConfirmContribute(payload: ConfirmContributePayload)

    fun openCustomContribute(payload: CustomContributePayload)

    fun setCustomBonus(payload: BonusPayload)

    fun openConfirmContribute(payload: ConfirmContributePayload)

    fun back()

    fun returnToMain()
}
