package jp.co.soramitsu.staking.impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.staking.api.domain.model.BlockNumber

@HelperBinding
fun bindBlockNumber(dynamicInstance: Any?): BlockNumber {
    return dynamicInstance.cast()
}
