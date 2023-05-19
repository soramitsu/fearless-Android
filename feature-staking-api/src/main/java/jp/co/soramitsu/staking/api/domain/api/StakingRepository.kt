package jp.co.soramitsu.staking.api.domain.api

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.runtime.AccountId
import java.math.BigInteger

interface StakingRepository {
    suspend fun getTotalIssuance(chainId: ChainId): BigInteger

    suspend fun getAccountInfo(chainId: ChainId, accountId: AccountId): AccountInfo
}
