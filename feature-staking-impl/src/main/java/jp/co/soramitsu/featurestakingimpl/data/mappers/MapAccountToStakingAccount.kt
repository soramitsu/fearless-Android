package jp.co.soramitsu.featurestakingimpl.data.mappers

import jp.co.soramitsu.featureaccountapi.domain.model.MetaAccount
import jp.co.soramitsu.featureaccountapi.domain.model.address
import jp.co.soramitsu.featurestakingapi.domain.model.StakingAccount
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun mapAccountToStakingAccount(chain: Chain, metaAccount: MetaAccount) = metaAccount.address(chain)?.let { address ->
    StakingAccount(
        address = address,
        name = metaAccount.name,
        chain.isEthereumBased
    )
}
