package jp.co.soramitsu.featurestakingimpl.data.network.blockhain.updaters.historical

import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import java.math.BigInteger

class HistoricalValidatorRewardPointsUpdater : HistoricalUpdater {

    override fun constructHistoricalKey(runtime: RuntimeSnapshot, era: BigInteger): String {
        return runtime.metadata.staking().storage("ErasRewardPoints").storageKey(runtime, era)
    }
}
