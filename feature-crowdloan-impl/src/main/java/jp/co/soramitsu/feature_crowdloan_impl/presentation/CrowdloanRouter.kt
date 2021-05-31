package jp.co.soramitsu.feature_crowdloan_impl.presentation

import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload

interface CrowdloanRouter {

    fun openContribute(payload: ContributePayload)

    fun openConfirmContribute(payload: ConfirmContributePayload)

    fun back()

    fun returnToMain()
}
