package jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.bifrost

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.featurecrowdloanimpl.data.network.api.bifrost.BifrostApi
import jp.co.soramitsu.featurecrowdloanimpl.data.network.api.bifrost.getAccountByReferralCode
import jp.co.soramitsu.featurecrowdloanimpl.data.network.blockhain.extrinsic.addMemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BifrostContributeInteractor(
    private val bifrostApi: BifrostApi,
    private val httpExceptionHandler: HttpExceptionHandler
) {

    suspend fun isCodeValid(code: String): Boolean {
        val response = httpExceptionHandler.wrap { bifrostApi.getAccountByReferralCode(code) }

        return response.data.getAccountByInvitationCode.account.isNullOrEmpty().not()
    }

    suspend fun submitOnChain(
        paraId: ParaId,
        referralCode: String,
        extrinsicBuilder: ExtrinsicBuilder
    ) = withContext(Dispatchers.Default) {
        extrinsicBuilder.addMemo(paraId, referralCode.toByteArray())
    }
}
