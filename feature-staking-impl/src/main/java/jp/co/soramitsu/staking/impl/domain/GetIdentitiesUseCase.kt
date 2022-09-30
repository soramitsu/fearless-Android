package jp.co.soramitsu.staking.impl.domain

import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.api.AccountIdMap
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.Identity

class GetIdentitiesUseCase(private val identitiesRepository: IdentityRepository) {

    suspend operator fun invoke(chain: Chain, vararg addresses: String): AccountIdMap<Identity?> {
        return identitiesRepository.getIdentitiesFromIds(chain, addresses.toList().map { it.toHexAccountId() })
    }
}
