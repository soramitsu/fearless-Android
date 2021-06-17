package jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import java.math.BigInteger

typealias TrieIndex = BigInteger

@HelperBinding
fun bindTrieIndex(dynamicInstance: Any?) = bindNumber(dynamicInstance)
