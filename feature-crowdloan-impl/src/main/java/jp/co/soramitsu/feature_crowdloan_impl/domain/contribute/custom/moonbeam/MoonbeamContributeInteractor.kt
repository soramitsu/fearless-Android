package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi

class MoonbeamContributeInteractor(
    private val moonbeamApi: MoonbeamApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    val fearlessReferralCode: String,
) {


}
