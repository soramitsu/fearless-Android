package jp.co.soramitsu.staking.impl.data.mappers

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.StakingAccount

fun mapAccountToStakingAccount(chain: Chain, metaAccount: MetaAccount) = metaAccount.address(chain)?.let { address ->
    StakingAccount(
        address = address,
        name = metaAccount.name,
        chain.isEthereumBased
    )
}
