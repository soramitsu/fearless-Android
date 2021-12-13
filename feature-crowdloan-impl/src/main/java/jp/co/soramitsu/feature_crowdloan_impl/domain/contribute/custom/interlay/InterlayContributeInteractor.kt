package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.interlay

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.extrinsic.addMemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InterlayContributeInteractor {

    fun isReferralValid(address: String) = address.length == 66

    suspend fun submitMemo(
        paraId: ParaId,
        referralCode: String,
        extrinsicBuilder: ExtrinsicBuilder
    ) = withContext(Dispatchers.Default) {
        extrinsicBuilder.addMemo(paraId, referralCode.fromHex())
    }
}
