package jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.astar

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountRepository
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.featurecrowdloanimpl.data.network.blockhain.extrinsic.addMemo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AstarContributeInteractor(
    private val accountRepository: AccountRepository
) {

    suspend fun isReferralValid(address: String, chainId: ChainId) =
        accountRepository.isInCurrentNetwork(address, chainId)

    suspend fun submitMemo(
        paraId: ParaId,
        referralCode: String,
        extrinsicBuilder: ExtrinsicBuilder
    ) = withContext(Dispatchers.Default) {
        extrinsicBuilder.addMemo(paraId, referralCode.toAccountId())
    }
}
