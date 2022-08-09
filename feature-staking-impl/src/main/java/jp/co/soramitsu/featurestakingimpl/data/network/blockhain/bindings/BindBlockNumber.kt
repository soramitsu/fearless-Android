package jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.featurestakingapi.domain.model.BlockNumber

@HelperBinding
fun bindBlockNumber(dynamicInstance: Any?): BlockNumber {
    return dynamicInstance.cast()
}
