package jp.co.soramitsu.feature_crowdloan_impl.presentation

import androidx.lifecycle.LiveData
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload

interface CrowdloanRouter {

    fun openContribute(payload: ContributePayload)

    val customBonusLiveData: LiveData<CustomContributePayload?>

    fun setCustomBonus(payload: CustomContributePayload)

    fun openConfirmContribute(payload: ConfirmContributePayload)

    fun back()

    fun returnToMain()
}
