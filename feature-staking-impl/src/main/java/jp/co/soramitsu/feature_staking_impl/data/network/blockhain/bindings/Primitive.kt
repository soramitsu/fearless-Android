package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import java.math.BigInteger

@HelperBinding
fun bindNumber(dynamicInstance: Any?): BigInteger = dynamicInstance.cast()
