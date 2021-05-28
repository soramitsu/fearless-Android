package jp.co.soramitsu.feature_crowdloan_impl.presentation

import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeFragment
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import kotlinx.coroutines.flow.Flow

interface CrowdloanRouter {

    fun openContribute(payload: ContributePayload)

    val customBonusFlow: Flow<BonusPayload?>

    val latestCustomBonus: BonusPayload?

    fun openCustomContribute(payload: CustomContributePayload)

    fun setCustomBonus(payload: BonusPayload)

    fun openConfirmContribute(payload: ConfirmContributePayload)

    fun back()

    fun returnToMain()
}
